package com.android.purebilibili.feature.video.ui.pager

import android.content.Context
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.android.purebilibili.core.store.TodayWatchDislikedVideoSnapshot
import com.android.purebilibili.core.store.TodayWatchFeedbackStore
import com.android.purebilibili.core.store.withDislikedVideoFeedback
import com.android.purebilibili.data.model.response.RecommendationFeedbackReason
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.data.model.response.toVideoItem
import com.android.purebilibili.data.repository.BlockedUpRepository
import com.android.purebilibili.feature.home.HomeNotInterestedAction
import com.android.purebilibili.feature.home.components.HomeNotInterestedReasonSheet
import com.android.purebilibili.feature.home.filterHomeVideosByNotInterestedFeedback
import com.android.purebilibili.feature.home.resolveHomeNotInterestedAction
import com.android.purebilibili.feature.home.resolveHomeNotInterestedReasons
import com.android.purebilibili.feature.plugin.QuickContentFilterHost
import com.android.purebilibili.feature.plugin.QuickContentFilterTarget
import kotlinx.coroutines.launch

/** 竖屏页条目（详情 / 推荐 / 合集项）统一转成首页「不感兴趣」使用的 VideoItem。 */
internal fun portraitPageItemAsVideoItem(item: Any?): VideoItem? = when (item) {
    is ViewInfo -> VideoItem(
        id = item.aid, bvid = item.bvid, aid = item.aid, cid = item.cid, title = item.title,
        pic = item.pic, owner = item.owner, stat = item.stat, tname = item.tname, pubdate = item.pubdate,
        isVertical = item.dimension?.isVertical == true,
    )
    is RelatedVideo -> item.toVideoItem()
    else -> null
}?.takeIf { it.bvid.isNotBlank() }

/** 被标记为不感兴趣后，竖屏流里排在后面的同一视频 / 同一 UP 主 / 同类关键词条目也一并移除。 */
internal fun portraitItemMatchesNotInterested(item: Any, action: HomeNotInterestedAction): Boolean {
    val video = portraitPageItemAsVideoItem(item) ?: return false
    return filterHomeVideosByNotInterestedFeedback(
        videos = listOf(video),
        dislikedBvids = setOf(action.bvid),
        dislikedCreatorMids = if (action.shouldBlockCreator) setOf(action.creatorMid) else emptySet(),
        dislikedKeywords = action.keywords,
    ).isEmpty()
}

/**
 * 与首页「不感兴趣」相同的本地落盘：记录到今日推荐负反馈（视频 / UP 主 / 关键词），
 * 选择「UP 主」时加入屏蔽列表并同步到哔哩哔哩黑名单（与首页行为一致）。
 */
internal fun persistPortraitNotInterested(context: Context, video: VideoItem, action: HomeNotInterestedAction) {
    val appContext = context.applicationContext
    com.android.purebilibili.core.coroutines.AppScope.ioScope.launch {
        runCatching {
            val updated = TodayWatchFeedbackStore.getSnapshot(appContext).withDislikedVideoFeedback(
                video = TodayWatchDislikedVideoSnapshot(
                    bvid = video.bvid,
                    title = video.title,
                    creatorName = video.owner.name,
                    creatorMid = video.owner.mid,
                    dislikedAtMillis = System.currentTimeMillis(),
                ),
                keywords = action.keywords,
                includeCreatorSignal = action.shouldBlockCreator,
            )
            TodayWatchFeedbackStore.saveSnapshot(appContext, updated)
        }
        if (action.shouldBlockCreator) {
            val users = BlockedUpRepository.getInstance(appContext)
            runCatching {
                if (action.shouldSyncCreatorToBilibiliBlockedList) {
                    // 先写本地屏蔽，再尝试同步黑名单；同步失败不影响本地生效。
                    users.blockUpWithBilibiliSync(
                        mid = action.creatorMid,
                        name = action.creatorName,
                        face = action.creatorFace,
                    )
                } else {
                    users.blockUp(action.creatorMid, action.creatorName, action.creatorFace)
                }
            }
        }
    }
}

/**
 * 竖屏流的「不感兴趣」弹层：复用首页的原因选择与「按关键词 / UP 主精细屏蔽」编辑器。
 * [onApplied] 在本地屏蔽生效后调用，由竖屏流负责移除后续同类条目并切到下一条。
 */
@Composable
internal fun BoxScope.PortraitNotInterestedHost(
    target: VideoItem?,
    onDismiss: () -> Unit,
    onApplied: (VideoItem, HomeNotInterestedAction) -> Unit,
) {
    val context = LocalContext.current
    var editingTarget by remember { mutableStateOf<QuickContentFilterTarget?>(null) }
    fun apply(video: VideoItem, reason: RecommendationFeedbackReason) {
        val action = resolveHomeNotInterestedAction(video, reason)
        persistPortraitNotInterested(context, video, action)
        onApplied(video, action)
        android.widget.Toast.makeText(context, "已在本地屏蔽所选内容", android.widget.Toast.LENGTH_SHORT).show()
    }
    if (target != null) {
        val reasons = remember(target.bvid) { resolveHomeNotInterestedReasons(target) }
        HomeNotInterestedReasonSheet(
            video = target,
            reasons = reasons,
            onReasonSelected = { reason ->
                onDismiss()
                apply(target, reason)
            },
            onQuickFilterClick = {
                onDismiss()
                reasons.firstOrNull()?.let { apply(target, it) }
                editingTarget = QuickContentFilterTarget.fromVideo(target)
            },
            onDismissRequest = onDismiss,
        )
    }
    QuickContentFilterHost(
        pendingFeedback = null,
        onDismissFeedback = {},
        editingTarget = editingTarget,
        onOpenEditor = { editingTarget = it },
        onDismissEditor = { editingTarget = null },
        modifier = Modifier.align(Alignment.BottomCenter),
    )
}
