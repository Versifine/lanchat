package com.lanchat.application.service;

import com.lanchat.application.config.AppSettings;
import com.lanchat.application.dto.MessageView;
import com.lanchat.domain.exception.BusinessException;
import com.lanchat.domain.model.Message;
import com.lanchat.domain.model.User;
import com.lanchat.domain.port.MessageRateLimiter;
import com.lanchat.domain.port.MessageRepository;
import com.lanchat.domain.port.RealtimeNotifier;
import com.lanchat.domain.port.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 聊天用例单元测试：消息校验、群聊/私聊路由、离线补发、历史条数规范化
 * （EV-FUNC-08~11、EV-SEC-05）。
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final User ALICE = new User(1L, "alice", "爱丽丝");
    private static final User BOB = new User(2L, "bob", "鲍勃");
    private static final AppSettings SETTINGS = new AppSettings(50, 20L * 1024 * 1024);

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RealtimeNotifier realtimeNotifier;
    @Mock
    private MessageRateLimiter messageRateLimiter;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(messageRepository, userRepository, realtimeNotifier,
                messageRateLimiter, SETTINGS);
    }

    @Test
    void groupMessageShouldBePersistedAndBroadcast() {
        when(messageRateLimiter.tryAcquire(1L)).thenReturn(true);
        when(userRepository.findPublicById(1L)).thenReturn(Optional.of(ALICE));
        when(messageRepository.insert(eq(1L), isNull(), eq("大家好"), anyLong(), eq(true))).thenReturn(10L);

        MessageView view = chatService.send(1L, null, "大家好");

        assertThat(view.id()).isEqualTo(10L);
        assertThat(view.receiverId()).isNull();
        assertThat(view.sender().id()).isEqualTo(1L);
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(realtimeNotifier).broadcastMessage(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(10L);
        assertThat(captor.getValue().content()).isEqualTo("大家好");
        assertThat(captor.getValue().isGroup()).isTrue();
    }

    @Test
    void onlinePrivateMessageShouldBePersistedDeliveredAndSentToBothSides() {
        when(messageRateLimiter.tryAcquire(1L)).thenReturn(true);
        when(userRepository.findPublicById(1L)).thenReturn(Optional.of(ALICE));
        when(userRepository.findPublicById(2L)).thenReturn(Optional.of(BOB));
        when(realtimeNotifier.isOnline(2L)).thenReturn(true);
        when(messageRepository.insert(eq(1L), eq(2L), eq("私聊"), anyLong(), eq(true))).thenReturn(11L);

        MessageView view = chatService.send(1L, 2L, "私聊");

        assertThat(view.receiverId()).isEqualTo(2L);
        verify(realtimeNotifier).sendMessage(eq(2L), any(Message.class), eq(false));
        verify(realtimeNotifier).sendMessage(eq(1L), any(Message.class), eq(false));
    }

    @Test
    void offlinePrivateMessageShouldBePersistedUndeliveredAndOnlyEchoedToSender() {
        when(messageRateLimiter.tryAcquire(1L)).thenReturn(true);
        when(userRepository.findPublicById(1L)).thenReturn(Optional.of(ALICE));
        when(userRepository.findPublicById(2L)).thenReturn(Optional.of(BOB));
        when(realtimeNotifier.isOnline(2L)).thenReturn(false);
        when(messageRepository.insert(eq(1L), eq(2L), eq("离线私聊"), anyLong(), eq(false))).thenReturn(12L);

        chatService.send(1L, 2L, "离线私聊");

        verify(realtimeNotifier).sendMessage(eq(1L), any(Message.class), eq(false));
        verify(realtimeNotifier, never()).sendMessage(eq(2L), any(Message.class), anyBoolean());
    }

    @Test
    void sendShouldRejectEmptyAndTooLongContent() {
        assertThat(businessError(() -> chatService.send(1L, null, null)).code()).isEqualTo(1004);
        assertThat(businessError(() -> chatService.send(1L, null, "   ")).code()).isEqualTo(1004);
        assertThat(businessError(() -> chatService.send(1L, null, "x".repeat(2001))).code()).isEqualTo(1008);
        verifyNoInteractions(messageRepository, userRepository, realtimeNotifier);
    }

    @Test
    void sendShouldAcceptExactlyMaximumLengthContent() {
        when(messageRateLimiter.tryAcquire(1L)).thenReturn(true);
        when(userRepository.findPublicById(1L)).thenReturn(Optional.of(ALICE));
        when(messageRepository.insert(eq(1L), isNull(),
                argThat(value -> value != null && value.length() == 2000), anyLong(), eq(true))).thenReturn(13L);

        MessageView view = chatService.send(1L, null, "x".repeat(2000));

        assertThat(view.id()).isEqualTo(13L);
    }

    @Test
    void sendShouldReturnRateLimitedWhenLimiterRejects() {
        when(messageRateLimiter.tryAcquire(1L)).thenReturn(false);

        BusinessException error = businessError(() -> chatService.send(1L, null, "hi"));

        assertThat(error.code()).isEqualTo(1009);
        verify(messageRepository, never()).insert(anyLong(), any(), anyString(), anyLong(), anyBoolean());
    }

    @Test
    void sendPrivateToSelfShouldBeRejected() {
        when(messageRateLimiter.tryAcquire(1L)).thenReturn(true);
        when(userRepository.findPublicById(1L)).thenReturn(Optional.of(ALICE));

        BusinessException error = businessError(() -> chatService.send(1L, 1L, "hi"));

        assertThat(error.code()).isEqualTo(1004);
    }

    @Test
    void sendPrivateToUnknownReceiverShouldReturn1005() {
        when(messageRateLimiter.tryAcquire(1L)).thenReturn(true);
        when(userRepository.findPublicById(1L)).thenReturn(Optional.of(ALICE));
        when(userRepository.findPublicById(99L)).thenReturn(Optional.empty());

        BusinessException error = businessError(() -> chatService.send(1L, 99L, "hi"));

        assertThat(error.code()).isEqualTo(1005);
    }

    @Test
    void replayOfflineMessagesShouldPushEachMessageAsOffline() {
        Message first = new Message(21L, BOB, 1L, "离线一", 1000L, false);
        Message second = new Message(22L, BOB, 1L, "离线二", 2000L, false);
        when(messageRepository.claimUndelivered(1L)).thenReturn(List.of(first, second));

        chatService.replayOfflineMessages(1L);

        verify(realtimeNotifier).sendMessage(1L, first, true);
        verify(realtimeNotifier).sendMessage(1L, second, true);
    }

    @Test
    void replayOfflineMessagesShouldDoNothingWhenNoPendingMessages() {
        when(messageRepository.claimUndelivered(1L)).thenReturn(List.of());

        chatService.replayOfflineMessages(1L);

        verifyNoInteractions(realtimeNotifier);
    }

    @Test
    void groupHistoryShouldNormalizeLimitToContractRange() {
        when(messageRepository.findGroupHistory(200)).thenReturn(List.of());

        chatService.groupHistory(999);

        verify(messageRepository).findGroupHistory(200);
    }

    @Test
    void groupHistoryShouldUseDefaultLimitForNonPositive() {
        when(messageRepository.findGroupHistory(50)).thenReturn(List.of());

        chatService.groupHistory(0);
        chatService.groupHistory(-5);

        verify(messageRepository, times(2)).findGroupHistory(50);
    }

    @Test
    void privateHistoryShouldReturnBidirectionalMessages() {
        Message message = new Message(31L, BOB, 1L, "历史", 1000L, true);
        when(userRepository.findPublicById(2L)).thenReturn(Optional.of(BOB));
        when(messageRepository.findPrivateHistory(1L, 2L, 50)).thenReturn(List.of(message));

        List<MessageView> history = chatService.privateHistory(1L, 2L, 0);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).content()).isEqualTo("历史");
        verify(messageRepository).findPrivateHistory(1L, 2L, 50);
    }

    @Test
    void privateHistoryShouldRejectSelfAndUnknownPeer() {
        assertThat(businessError(() -> chatService.privateHistory(1L, 1L, 50)).code()).isEqualTo(1004);

        when(userRepository.findPublicById(99L)).thenReturn(Optional.empty());
        assertThat(businessError(() -> chatService.privateHistory(1L, 99L, 50)).code()).isEqualTo(1005);
    }

    private static BusinessException businessError(Executable action) {
        Throwable throwable = catchThrowable(action::execute);
        assertThat(throwable).isInstanceOf(BusinessException.class);
        return (BusinessException) throwable;
    }
}
