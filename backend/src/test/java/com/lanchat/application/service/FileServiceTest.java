package com.lanchat.application.service;

import com.lanchat.application.config.AppSettings;
import com.lanchat.application.dto.FileDownload;
import com.lanchat.application.dto.FileView;
import com.lanchat.domain.exception.BusinessException;
import com.lanchat.domain.model.ChatFile;
import com.lanchat.domain.model.StoredFile;
import com.lanchat.domain.model.User;
import com.lanchat.domain.port.FileRepository;
import com.lanchat.domain.port.FileStorage;
import com.lanchat.domain.port.RealtimeNotifier;
import com.lanchat.domain.port.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 文件用例单元测试：清洗文件名、大小限制、落盘 + 入库 + WS 通知、元数据与下载（EV-FUNC-12/13、EV-SEC-06/07）。
 */
@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    private static final AppSettings SETTINGS = new AppSettings(50, 1_000L);
    private static final User ALICE = new User(1L, "alice", "爱丽丝");
    private static final User BOB = new User(2L, "bob", "鲍勃");

    @Mock
    private FileRepository fileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FileStorage fileStorage;
    @Mock
    private RealtimeNotifier realtimeNotifier;

    private FileService fileService;

    @BeforeEach
    void setUp() {
        fileService = new FileService(fileRepository, userRepository, fileStorage, realtimeNotifier, SETTINGS);
    }

    @Test
    void uploadShouldSanitizeNamePersistAndNotifyReceiver() {
        when(userRepository.findPublicById(1L)).thenReturn(Optional.of(ALICE));
        when(userRepository.findPublicById(2L)).thenReturn(Optional.of(BOB));
        when(fileStorage.store(any(InputStream.class), eq("evil.sh"), eq(1_000L)))
                .thenReturn(new StoredFile("uuid.sh", 5L, "hash"));
        when(fileRepository.insert(eq(1L), eq("evil.sh"), eq("uuid.sh"), eq(5L), eq("hash"), anyLong()))
                .thenReturn(7L);

        FileView view = fileService.upload(1L, 2L, "../../evil.sh", 5L,
                new ByteArrayInputStream("hello".getBytes()));

        assertThat(view.id()).isEqualTo(7L);
        assertThat(view.originalName()).isEqualTo("evil.sh");
        assertThat(view.size()).isEqualTo(5L);
        assertThat(view.downloadUrl()).isEqualTo("/api/files/7/download");
        ArgumentCaptor<ChatFile> fileCaptor = ArgumentCaptor.forClass(ChatFile.class);
        verify(realtimeNotifier).sendFile(eq(2L), fileCaptor.capture(), eq(ALICE));
        assertThat(fileCaptor.getValue().originalName()).isEqualTo("evil.sh");
        assertThat(fileCaptor.getValue().storedName()).isEqualTo("uuid.sh");
    }

    @Test
    void uploadWithoutReceiverShouldNotNotifyAnyone() {
        when(userRepository.findPublicById(1L)).thenReturn(Optional.of(ALICE));
        when(fileStorage.store(any(InputStream.class), eq("a.txt"), eq(1_000L)))
                .thenReturn(new StoredFile("uuid.txt", 3L, "hash"));
        when(fileRepository.insert(eq(1L), eq("a.txt"), eq("uuid.txt"), eq(3L), eq("hash"), anyLong()))
                .thenReturn(8L);

        fileService.upload(1L, null, "a.txt", 3L, new ByteArrayInputStream("abc".getBytes()));

        verify(realtimeNotifier, never()).sendFile(anyLong(), any(ChatFile.class), any(User.class));
    }

    @Test
    void uploadShouldFallbackToUnnamedForBlankFilename() {
        when(userRepository.findPublicById(1L)).thenReturn(Optional.of(ALICE));
        when(fileStorage.store(any(InputStream.class), eq("unnamed"), eq(1_000L)))
                .thenReturn(new StoredFile("uuid", 3L, "hash"));
        when(fileRepository.insert(eq(1L), eq("unnamed"), eq("uuid"), eq(3L), eq("hash"), anyLong()))
                .thenReturn(9L);

        fileService.upload(1L, null, "   ", 3L, new ByteArrayInputStream("abc".getBytes()));

        verify(fileStorage).store(any(InputStream.class), eq("unnamed"), eq(1_000L));
    }

    @Test
    void uploadShouldRejectTooLargeDeclaredSizeBeforeStoring() {
        BusinessException error = businessError(() -> fileService.upload(1L, null, "big.bin", 1_001L,
                new ByteArrayInputStream(new byte[0])));

        assertThat(error.code()).isEqualTo(1006);
        verifyNoInteractions(fileStorage);
    }

    @Test
    void uploadShouldRejectUnknownUploaderOrReceiver() {
        when(userRepository.findPublicById(1L)).thenReturn(Optional.empty());
        assertThat(businessError(() -> fileService.upload(1L, null, "a.txt", 1L,
                new ByteArrayInputStream(new byte[0]))).code()).isEqualTo(1001);

        when(userRepository.findPublicById(3L)).thenReturn(Optional.of(ALICE));
        when(userRepository.findPublicById(99L)).thenReturn(Optional.empty());
        assertThat(businessError(() -> fileService.upload(3L, 99L, "a.txt", 1L,
                new ByteArrayInputStream(new byte[0]))).code()).isEqualTo(1005);
    }

    @Test
    void uploadShouldCleanupStoredFileWhenRepositoryInsertFails() {
        when(userRepository.findPublicById(1L)).thenReturn(Optional.of(ALICE));
        when(fileStorage.store(any(InputStream.class), eq("a.txt"), eq(1_000L)))
                .thenReturn(new StoredFile("uuid.txt", 1L, "hash"));
        when(fileRepository.insert(eq(1L), eq("a.txt"), eq("uuid.txt"), eq(1L), eq("hash"), anyLong()))
                .thenThrow(new IllegalStateException("db down"));

        Throwable throwable = catchThrowable(() -> fileService.upload(1L, null, "a.txt", 1L,
                new ByteArrayInputStream("x".getBytes())));

        assertThat(throwable).isInstanceOf(IllegalStateException.class).hasMessage("db down");
        verify(fileStorage).deleteQuietly("uuid.txt");
    }

    @Test
    void metadataShouldReturnFileOr1007() {
        ChatFile file = new ChatFile(7L, 1L, "report.pdf", "uuid.pdf", 100L, "hash", 1000L);
        when(fileRepository.findById(7L)).thenReturn(Optional.of(file));

        assertThat(fileService.metadata(7L).downloadUrl()).isEqualTo("/api/files/7/download");

        when(fileRepository.findById(8L)).thenReturn(Optional.empty());
        assertThat(businessError(() -> fileService.metadata(8L)).code()).isEqualTo(1007);
    }

    @Test
    void downloadShouldOpenStoredStreamOr1007() {
        ChatFile file = new ChatFile(7L, 1L, "report.pdf", "uuid.pdf", 100L, "hash", 1000L);
        when(fileRepository.findById(7L)).thenReturn(Optional.of(file));
        when(fileStorage.open("uuid.pdf")).thenReturn(new ByteArrayInputStream("data".getBytes()));

        FileDownload download = fileService.download(7L);

        assertThat(download.file().id()).isEqualTo(7L);
        assertThat(download.content()).isNotNull();

        when(fileRepository.findById(9L)).thenReturn(Optional.empty());
        assertThat(businessError(() -> fileService.download(9L)).code()).isEqualTo(1007);
    }

    private static BusinessException businessError(Executable action) {
        Throwable throwable = catchThrowable(action::execute);
        assertThat(throwable).isInstanceOf(BusinessException.class);
        return (BusinessException) throwable;
    }
}
