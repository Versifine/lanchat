package com.lanchat.adapter.out.persistence;

import com.lanchat.domain.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 消息仓储测试（临时 SQLite + schema.sql）：历史升序、双向私聊、离线私聊原子领取（EV-FUNC-08/10/11）。
 */
class JdbcMessageRepositoryTest extends SqliteRepositoryTestSupport {

    private JdbcMessageRepository repository;
    private long aliceId;
    private long bobId;
    private long carolId;

    @BeforeEach
    void setUp() {
        repository = new JdbcMessageRepository(jdbcTemplate, transactionManager);
        aliceId = insertUser("alice");
        bobId = insertUser("bob");
        carolId = insertUser("carol");
    }

    @Test
    void groupHistoryShouldReturnLatestLimitInAscendingOrder() {
        repository.insert(aliceId, null, "g1", 1_000L, true);
        repository.insert(bobId, null, "g2", 2_000L, true);
        repository.insert(aliceId, null, "g3", 3_000L, true);
        repository.insert(carolId, null, "g4", 4_000L, true);
        // 私聊消息不得出现在群聊历史里
        repository.insert(aliceId, bobId, "p1", 5_000L, true);

        List<Message> all = repository.findGroupHistory(50);
        assertThat(all).extracting(Message::content).containsExactly("g1", "g2", "g3", "g4");
        assertThat(all.get(0).createdAt()).isLessThan(all.get(3).createdAt());
        assertThat(all).allMatch(Message::isGroup);

        List<Message> latestTwo = repository.findGroupHistory(2);
        assertThat(latestTwo).extracting(Message::content).containsExactly("g3", "g4");
    }

    @Test
    void groupHistoryShouldBeEmptyWhenNoMessages() {
        assertThat(repository.findGroupHistory(50)).isEmpty();
    }

    @Test
    void privateHistoryShouldContainBothDirectionsAndExcludeOthers() {
        repository.insert(aliceId, bobId, "a-to-b", 1_000L, true);
        repository.insert(bobId, aliceId, "b-to-a", 2_000L, true);
        repository.insert(aliceId, carolId, "a-to-c", 3_000L, true);
        repository.insert(aliceId, null, "group", 4_000L, true);

        List<Message> history = repository.findPrivateHistory(aliceId, bobId, 50);

        assertThat(history).extracting(Message::content).containsExactly("a-to-b", "b-to-a");
        assertThat(history).allSatisfy(message -> {
            Set<Long> participants = Set.of(message.sender().id(), message.receiverId());
            assertThat(participants).containsExactlyInAnyOrder(aliceId, bobId);
        });
        // 顺序必须升序
        assertThat(history.get(0).createdAt()).isLessThan(history.get(1).createdAt());
    }

    @Test
    void claimUndeliveredShouldMarkAsDeliveredAndBeIdempotent() {
        long first = repository.insert(bobId, aliceId, "offline-1", 1_000L, false);
        long second = repository.insert(bobId, aliceId, "offline-2", 2_000L, false);
        repository.insert(bobId, aliceId, "already-delivered", 3_000L, true);
        repository.insert(aliceId, null, "group", 4_000L, true);
        repository.insert(aliceId, bobId, "from-alice", 5_000L, false);

        List<Message> claimed = repository.claimUndelivered(aliceId);

        assertThat(claimed).extracting(Message::id).containsExactly(first, second);
        assertThat(claimed).allMatch(Message::delivered);
        assertThat(claimed.get(0).sender().username()).isEqualTo("bob");
        assertThat(claimed.get(1).content()).isEqualTo("offline-2");
        // 已标记为 delivered，第二次领取为空（原子领取语义）
        assertThat(repository.claimUndelivered(aliceId)).isEmpty();
        // 别人的未投递消息不受影响
        assertThat(repository.claimUndelivered(bobId))
                .extracting(Message::content).containsExactly("from-alice");
    }

    @Test
    void claimUndeliveredShouldNotTouchMessagesForOtherReceiver() {
        repository.insert(aliceId, bobId, "for-bob", 1_000L, false);

        assertThat(repository.claimUndelivered(aliceId)).isEmpty();
        assertThat(repository.claimUndelivered(bobId)).hasSize(1);
    }

    @Test
    void concurrentClaimShouldNeverReturnSameMessageTwice() throws Exception {
        int total = 60;
        for (int i = 0; i < total; i++) {
            repository.insert(bobId, aliceId, "offline-" + i, 1_000L + i, false);
        }

        Queue<Long> claimed = new ConcurrentLinkedQueue<>();
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> claimOnce(aliceId, ready, start, claimed, errors));
            Future<?> second = pool.submit(() -> claimOnce(aliceId, ready, start, claimed, errors));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);

            List<Long> concurrentIds = List.copyOf(claimed);
            assertThat(concurrentIds).as("并发领取不得出现重复消息").doesNotHaveDuplicates();

            // 无论并发事务是否冲突，剩余消息都必须能继续领取；最终并集 = 全部消息
            for (Message message : repository.claimUndelivered(aliceId)) {
                claimed.add(message.id());
            }
            Set<Long> uniqueIds = new HashSet<>(claimed);
            assertThat(uniqueIds).hasSize(total);
            assertThat(uniqueIds).doesNotContain(0L);
            // SQLite 写锁冲突可能让其中一个并发事务抛异常（记录以便报告瓶颈），但不得重复投递
            errors.forEach(error -> System.out.println("[仓储并发观察] " + error.getClass().getSimpleName()));
        } finally {
            pool.shutdownNow();
        }
    }

    private void claimOnce(long receiverId,
                           CountDownLatch ready,
                           CountDownLatch start,
                           Queue<Long> claimed,
                           List<Throwable> errors) {
        ready.countDown();
        try {
            start.await(5, TimeUnit.SECONDS);
            for (Message message : repository.claimUndelivered(receiverId)) {
                claimed.add(message.id());
            }
        } catch (Throwable throwable) {
            errors.add(throwable);
        }
    }
}
