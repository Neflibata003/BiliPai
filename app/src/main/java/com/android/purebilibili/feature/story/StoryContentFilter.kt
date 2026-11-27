package com.android.purebilibili.feature.story

import com.android.purebilibili.core.filter.keepsVideo
import com.android.purebilibili.core.plugin.BuiltinPluginIds
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.store.ContentFilterRules
import com.android.purebilibili.core.store.ContentVideoSurface
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.StoryItem
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.model.response.toVideoItem
import com.android.purebilibili.feature.plugin.filterVideoTagMatches
import com.android.purebilibili.plugin.sdk.FeedKind
import kotlinx.coroutines.CancellationException

/**
 * 竖屏流 / 刷视频推荐与首页推荐共用同一套屏蔽规则：
 * - 内容过滤插件：时长、播放量、点赞率、标题与分区关键词、白名单；
 * - 去广告增强：广告标题与视频标签屏蔽词；
 * - 跨页面共享规则：屏蔽词、拉黑的 UP 主。
 *
 * Story 接口偶尔不返回统计数据（播放量与点赞都是 0），这类条目只跳过播放量与点赞率两项，
 * 避免设置了「播放量过滤」后整页推荐被误判为低质而清空。过滤失败时保留原列表，不阻塞刷视频。
 */
internal suspend fun filterPortraitFeedRecommendations(items: List<RelatedVideo>): List<RelatedVideo> {
    if (items.isEmpty()) return items
    return try {
        PluginManager.awaitPluginReady(BuiltinPluginIds.AD_FILTER)
        PluginManager.awaitPluginReady(BuiltinPluginIds.FEED_FILTER)
        val originals = items.associateBy { it.aid to it.bvid }
        val candidates = items.map { it.toPortraitFilterCandidate() }
        val pluginKept = PluginManager
            .filterFeedItemsBeforeContentProjection(candidates, feedKind = FeedKind.HOME_RECOMMEND)
            .mapNotNull { originals[it.aid to it.bvid] }
        val tagKept = filterVideoTagMatches(pluginKept, bvid = { it.bvid }, asVideoItem = { it.toVideoItem() })
        val rules = com.android.purebilibili.core.network.NetworkModule.appContext
            ?.let { ContentFilterRules.snapshot(it) }
            ?: ContentFilterRules.current.value
            ?: return tagKept
        tagKept.filter {
            rules.keepsVideo(
                it.bvid, it.aid, it.title, it.owner.mid, it.owner.name,
                ContentVideoSurface.RECOMMEND,
            )
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        com.android.purebilibili.core.util.Logger.e("PortraitFeedFilter", "content filter failed", error)
        items
    }
}

/** Story 条目按稿件身份过滤，保持原有顺序与条目字段；无法转换为普通视频的条目交给后续可播放检查。 */
internal suspend fun filterStoryItemsByContentRules(items: List<StoryItem>): List<StoryItem> {
    if (items.isEmpty()) return items
    val videos = items.map(::storyItemToRelatedVideo)
    val kept = filterPortraitFeedRecommendations(videos.filterNotNull())
        .mapTo(hashSetOf()) { it.aid to it.bvid }
    return items.filterIndexed { index, _ ->
        val video = videos[index]
        video == null || (video.aid to video.bvid) in kept
    }
}

private fun RelatedVideo.toPortraitFilterCandidate(): VideoItem {
    val video = toVideoItem()
    val statsMissing = stat.view <= 0 && stat.like <= 0
    // 内容过滤插件在 view < 0 时跳过播放量与点赞率判断。
    return if (statsMissing) video.copy(stat = video.stat.copy(view = -1, like = -1)) else video
}
