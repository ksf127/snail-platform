package com.snail.platform.control.service;

import com.snail.platform.common.api.BizException;
import com.snail.platform.common.build.BuildMessage;
import com.snail.platform.common.model.TriggerType;
import com.snail.platform.control.entity.PipelineEntity;
import com.snail.platform.control.entity.PipelineRunEntity;
import com.snail.platform.control.entity.RepoEntity;
import com.snail.platform.control.integration.BuildMessagePublisher;
import com.snail.platform.control.mapper.PipelineMapper;
import com.snail.platform.control.mapper.PipelineRunMapper;
import com.snail.platform.control.mapper.RepoMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PipelineServiceTest {

    @Mock
    private PipelineMapper pipelineMapper;
    @Mock
    private PipelineRunMapper runMapper;
    @Mock
    private RepoMapper repoMapper;
    @Mock
    private ProjectService projectService;
    @Mock
    private BuildMessagePublisher publisher;

    private PipelineService service;

    @BeforeEach
    void setUp() {
        service = new PipelineService(pipelineMapper, runMapper, repoMapper, projectService, publisher,
                new ObjectMapper());
        ReflectionTestUtils.setField(service, "defaultExecutor", "DOCKER");
    }

    @Test
    void triggerCreatesQueuedRunAndPublishesMessage() {
        PipelineEntity pipeline = pipeline(10L, true, 5L, "main", "MANUAL");
        when(pipelineMapper.selectById(10L)).thenReturn(pipeline);
        when(repoMapper.selectById(5L)).thenReturn(repo(5L));
        when(runMapper.selectOne(any())).thenReturn(null);
        when(runMapper.insert(any(PipelineRunEntity.class))).thenAnswer(invocation -> {
            PipelineRunEntity run = invocation.getArgument(0);
            run.setId(100L);
            return 1;
        });

        PipelineRunEntity run = service.trigger(10L, TriggerType.MANUAL, "release", null, "admin");

        assertThat(run.getId()).isEqualTo(100L);
        assertThat(run.getBuildNo()).isEqualTo(1);
        assertThat(run.getStatus()).isEqualTo("QUEUED");
        assertThat(run.getBranch()).isEqualTo("release");
        assertThat(run.getTriggeredBy()).isEqualTo("admin");
        verify(publisher).publish(new BuildMessage(100L, 10L));
    }

    @Test
    void triggerIncrementsBuildNo() {
        PipelineEntity pipeline = pipeline(10L, true, 5L, "main", "MANUAL");
        PipelineRunEntity last = new PipelineRunEntity();
        last.setBuildNo(7);
        when(pipelineMapper.selectById(10L)).thenReturn(pipeline);
        when(repoMapper.selectById(5L)).thenReturn(repo(5L));
        when(runMapper.selectOne(any())).thenReturn(last);
        when(runMapper.insert(any(PipelineRunEntity.class))).thenAnswer(invocation -> {
            PipelineRunEntity run = invocation.getArgument(0);
            run.setId(101L);
            return 1;
        });

        PipelineRunEntity run = service.trigger(10L, TriggerType.MANUAL, null, "sha1", "admin");

        assertThat(run.getBuildNo()).isEqualTo(8);
        assertThat(run.getBranch()).isEqualTo("main");
        assertThat(run.getCommitSha()).isEqualTo("sha1");
    }

    @Test
    void triggerRejectsDisabledPipeline() {
        when(pipelineMapper.selectById(10L)).thenReturn(pipeline(10L, false, 5L, "main", "MANUAL"));

        assertThatThrownBy(() -> service.trigger(10L, TriggerType.MANUAL, null, null, "admin"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("禁用");
        verify(publisher, never()).publish(any());
    }

    @Test
    void triggerRejectsPipelineWithoutRepo() {
        when(pipelineMapper.selectById(10L)).thenReturn(pipeline(10L, true, null, "main", "MANUAL"));

        assertThatThrownBy(() -> service.trigger(10L, TriggerType.MANUAL, null, null, "admin"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未绑定可用仓库");
    }

    @Test
    void triggerMarksRunFailedWhenPublishFails() {
        when(pipelineMapper.selectById(10L)).thenReturn(pipeline(10L, true, 5L, "main", "MANUAL"));
        when(repoMapper.selectById(5L)).thenReturn(repo(5L));
        when(runMapper.selectOne(any())).thenReturn(null);
        when(runMapper.insert(any(PipelineRunEntity.class))).thenAnswer(invocation -> {
            PipelineRunEntity run = invocation.getArgument(0);
            run.setId(102L);
            return 1;
        });
        doThrow(new IllegalStateException("broker down")).when(publisher).publish(any());

        assertThatThrownBy(() -> service.trigger(10L, TriggerType.MANUAL, null, null, "admin"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("入队失败");

        ArgumentCaptor<PipelineRunEntity> captor = ArgumentCaptor.forClass(PipelineRunEntity.class);
        verify(runMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getErrorMessage()).contains("broker down");
        assertThat(captor.getValue().getFinishedAt()).isNotNull();
    }

    @Test
    void webhookTriggersPipelinesMatchingBranchOrWildcard() {
        RepoEntity repo = repo(5L);
        PipelineEntity wildcard = pipeline(1L, true, 5L, "*", "WEBHOOK");
        PipelineEntity dev = pipeline(2L, true, 5L, "dev", "WEBHOOK");
        when(pipelineMapper.selectList(any())).thenReturn(List.of(wildcard, dev));
        when(pipelineMapper.selectById(1L)).thenReturn(wildcard);
        when(pipelineMapper.selectById(2L)).thenReturn(dev);
        when(repoMapper.selectById(5L)).thenReturn(repo);
        when(runMapper.selectOne(any())).thenReturn(null);
        AtomicLong nextId = new AtomicLong(200L);
        when(runMapper.insert(any(PipelineRunEntity.class))).thenAnswer(invocation -> {
            PipelineRunEntity run = invocation.getArgument(0);
            run.setId(nextId.getAndIncrement());
            return 1;
        });

        List<Long> runIds = service.triggerWebhook(repo, "dev", "abcdef");

        assertThat(runIds).containsExactly(200L, 201L);
        verify(publisher, times(2)).publish(any());
    }

    @Test
    void webhookIgnoresPipelinesOnOtherBranches() {
        RepoEntity repo = repo(5L);
        PipelineEntity release = pipeline(3L, true, 5L, "release", "WEBHOOK");
        when(pipelineMapper.selectList(any())).thenReturn(List.of(release));

        List<Long> runIds = service.triggerWebhook(repo, "dev", "abcdef");

        assertThat(runIds).isEmpty();
        verify(publisher, never()).publish(any());
    }

    private PipelineEntity pipeline(Long id, boolean enabled, Long repoId, String branch, String triggerType) {
        PipelineEntity pipeline = new PipelineEntity();
        pipeline.setId(id);
        pipeline.setProjectId(1L);
        pipeline.setRepoId(repoId);
        pipeline.setName("pipeline-" + id);
        pipeline.setBranch(branch);
        pipeline.setTriggerType(triggerType);
        pipeline.setExecutorType("DOCKER");
        pipeline.setDefinitionJson("{\"steps\":[]}");
        pipeline.setEnabled(enabled);
        return pipeline;
    }

    private RepoEntity repo(Long id) {
        RepoEntity repo = new RepoEntity();
        repo.setId(id);
        repo.setProjectId(1L);
        repo.setName("repo-" + id);
        repo.setCloneUrl("https://git.example.com/repo.git");
        repo.setDefaultBranch("main");
        return repo;
    }
}
