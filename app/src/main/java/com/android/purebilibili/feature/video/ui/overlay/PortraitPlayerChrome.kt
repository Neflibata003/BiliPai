package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.android.purebilibili.core.ui.components.appHorizontalScroll as horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.outlined.ClosedCaptionOff
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.constrainHeight
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.feature.video.ui.components.NativeDanmakuToggleButton
import com.android.purebilibili.feature.video.ui.components.PiliPlusDanmakuSendIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.appActionWithFeedback
import com.android.purebilibili.core.store.FullscreenTopBarItem
import com.android.purebilibili.core.store.FullscreenTopBarSettings
import com.android.purebilibili.core.store.PortraitPlayerChromeSettings
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 竖屏信息区的统计行：「12.3万播放 · 1234人正在看」；两项都没有时不显示。 */
internal fun resolvePortraitStatsText(playCountText: String, onlineCountText: String): String? =
    listOfNotNull(
        playCountText.trim().takeIf { it.isNotEmpty() && it != "0" }?.let { "${it}播放" },
        onlineCountText.trim().takeIf { it.isNotEmpty() },
    ).joinToString(" · ").ifEmpty { null }

/** 竖屏播放是否保留系统状态栏（时间 / 电量 / 信号）。首帧使用内存缓存，避免状态栏闪烁。 */
@Composable
internal fun rememberPortraitPlayerShowStatusBar(): Boolean {
    val context = LocalContext.current
    val showStatusBar by remember(context) { PortraitPlayerChromeSettings.getShowStatusBar(context) }
        .collectAsStateWithLifecycle(initialValue = PortraitPlayerChromeSettings.peekShowStatusBar())
    return showStatusBar
}

/**
 * 竖屏播放保留状态栏时垫在状态栏后的淡渐变，保证浅色画面上的时间与电量依旧可读。
 * 状态栏隐藏（或下滑临时唤出）时 statusBars 高度为 0，此层不占任何空间。
 */
@Composable
internal fun PortraitStatusBarScrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.42f), Color.Transparent))),
    )
}

/** 竖屏「更多」菜单里的两个外观开关：状态栏显示与顶栏设备接力按钮。 */
internal class PortraitChromeMenuToggles(
    val statusBarShown: Boolean,
    val toggleStatusBar: () -> Unit,
    /** null 表示当前没有设备接力入口（功能关闭或宿主未提供）。 */
    val nearbyHandoffOnTopBar: Boolean?,
    val toggleNearbyHandoffOnTopBar: () -> Unit,
)

/** [scope] 必须来自菜单之外：点击后菜单立即关闭，菜单内的协程作用域会随之取消。 */
@Composable
internal fun rememberPortraitChromeMenuToggles(scope: CoroutineScope): PortraitChromeMenuToggles {
    val context = LocalContext.current
    val statusBarShown = rememberPortraitPlayerShowStatusBar()
    val nearbyOnTopBar = FullscreenTopBarItem.NEARBY_HANDOFF in rememberVisibleFullscreenTopBarItems()
    val nearbyAvailable = com.android.purebilibili.feature.nearby.NEARBY_HANDOFF_ENABLED &&
        com.android.purebilibili.feature.nearby.LocalNearbyHandoff.current != null
    return PortraitChromeMenuToggles(
        statusBarShown = statusBarShown,
        toggleStatusBar = {
            scope.launch { PortraitPlayerChromeSettings.setShowStatusBar(context, !statusBarShown) }
        },
        nearbyHandoffOnTopBar = nearbyOnTopBar.takeIf { nearbyAvailable },
        toggleNearbyHandoffOnTopBar = {
            scope.launch {
                FullscreenTopBarSettings.setVisible(context, FullscreenTopBarItem.NEARBY_HANDOFF, !nearbyOnTopBar)
            }
        },
    )
}

/** 竖屏全屏的信息区独立于顶部导航；展开状态只属于当前稿件与标题。 */
@Composable
internal fun PortraitPlayerMetadata(
    bvid: String,
    title: String,
    authorName: String,
    authorFace: String,
    isFollowing: Boolean,
    followStatusKnown: Boolean,
    onFollowClick: () -> Unit,
    episodeLabel: String?,
    onEpisodeClick: () -> Unit,
    collectionLabel: String?,
    onCollectionClick: () -> Unit,
    modifier: Modifier = Modifier,
    onAuthorClick: (() -> Unit)? = null,
    onOpenDetails: (() -> Unit)? = null,
    statsText: String? = null,
) {
    var expanded by remember(bvid, title) { mutableStateOf(false) }
    var titleOverflows by remember(bvid, title) { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (authorName.isNotBlank()) {
            PortraitPlayerAuthorRow(
                authorKey = bvid,
                authorName = authorName,
                authorFace = authorFace,
                isFollowing = isFollowing,
                followStatusKnown = followStatusKnown,
                onFollowClick = onFollowClick,
                modifier = Modifier.fillMaxWidth(),
                onAuthorClick = onAuthorClick,
            )
        }
        if (title.isNotBlank()) {
            AppText(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = if (expanded) 6 else 2,
                overflow = TextOverflow.Ellipsis,
                tapToCopyEnabled = false,
                onTextLayout = { if (!expanded) titleOverflows = it.hasVisualOverflow },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { stateDescription = if (expanded) "标题已展开" else "标题已收起" }
                    .clickable(
                        enabled = expanded || titleOverflows,
                        role = Role.Button,
                        onClickLabel = if (expanded) "收起标题" else "展开标题",
                        onClick = { expanded = !expanded },
                    )
                    .padding(vertical = 4.dp),
            )
        }
        if (!statsText.isNullOrBlank()) {
            AppText(
                text = statsText,
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                tapToCopyEnabled = false,
                modifier = Modifier.fillMaxWidth().testTag("portrait_player_stats"),
            )
        }
        listOfNotNull(
            episodeLabel?.let { it to onEpisodeClick },
            collectionLabel?.let { it to onCollectionClick },
        ).forEach { (label, onClick) ->
            // 只有玻璃开启时添加外壳；原生信息区直接显示选集入口。
            PlayerControlGlassGroup(enabled = LocalPlayerControlGlassEnabled.current) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClickLabel = "打开选集", onClick = appActionWithFeedback(onClick))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppIcon(Icons.Outlined.VideoLibrary, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    AppText(
                        text = label,
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        tapToCopyEnabled = false,
                        modifier = Modifier.weight(1f),
                    )
                    AppIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "选集", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (expanded || titleOverflows || onOpenDetails != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                if (expanded || titleOverflows) {
                    AppText(
                        text = if (expanded) "收起" else "展开标题",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        tapToCopyEnabled = false,
                        modifier = Modifier
                            .clickable(role = Role.Button, onClick = { expanded = !expanded })
                            .padding(vertical = 8.dp),
                    )
                }
                if (onOpenDetails != null) {
                    AppText(
                        text = "视频详情",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        tapToCopyEnabled = false,
                        modifier = Modifier
                            .clickable(role = Role.Button, onClick = onOpenDetails)
                            .padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** 竖屏底栏图标槽宽：比原 40dp 收窄，保证 390dp 级手机上「倍速 + 画质」完整显示而不被横向滚动裁切。 */
private val PortraitControlSlotWidth = 36.dp

/** 两条材质路径共用操作与触摸尺寸；只有玻璃开启时为左右两组提供独立外壳。 */
@Composable
internal fun PortraitPlaybackControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    speed: Float,
    quality: String,
    onSpeedClick: () -> Unit,
    onQualityClick: () -> Unit,
    speedModifier: Modifier,
    qualityModifier: Modifier,
    danmakuEnabled: Boolean,
    onDanmakuToggle: () -> Unit,
    onSendDanmakuClick: () -> Unit,
    subtitleControlState: SubtitleControlUiState,
    onSubtitleClick: () -> Unit,
    subtitleModifier: Modifier,
    onExitFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    onEnterLandscape: (() -> Unit)? = null,
) {
    val glass = LocalPlayerControlGlassEnabled.current
    val subtitleActive = subtitleControlState.trackAvailable && subtitleControlState.enabled
    val playback: @Composable () -> Unit = {
        PlayerControlGlassGroup(enabled = glass) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(PortraitControlSlotWidth)
                        .height(48.dp)
                        .testTag("player_play_pause")
                        .clickable(
                            role = Role.Button,
                            onClickLabel = if (isPlaying) "暂停播放" else "开始播放",
                            onClick = appActionWithFeedback(onPlayPause),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "暂停播放" else "开始播放",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                ProgressTimeText(
                    positionProvider = positionProvider,
                    durationProvider = durationProvider,
                    singleLine = true,
                    modifier = Modifier.padding(end = 4.dp).testTag("player_time_viewport"),
                )
            }
        }
    }
    val tools: @Composable () -> Unit = {
        PlayerControlGlassGroup(enabled = glass) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NativeDanmakuToggleButton(
                    enabled = danmakuEnabled,
                    onToggle = onDanmakuToggle,
                    activeTint = com.android.purebilibili.core.ui.AppContentColors.accent(),
                    inactiveTint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier
                        .width(PortraitControlSlotWidth)
                        .height(48.dp)
                        .testTag("player_danmaku_toggle"),
                )
                AppIconButton(
                    onClick = onSendDanmakuClick,
                    modifier = Modifier
                        .width(PortraitControlSlotWidth)
                        .height(48.dp)
                        .testTag("player_send_danmaku"),
                ) {
                    PiliPlusDanmakuSendIcon(size = 22.dp, tint = Color.White)
                }
                // 字幕入口常驻；轨道未就绪或不存在时由菜单说明，避免底栏入口忽隐忽现。
                AppIconButton(
                    onClick = onSubtitleClick,
                    modifier = Modifier.width(PortraitControlSlotWidth).height(48.dp).then(subtitleModifier).semantics {
                        stateDescription = if (subtitleActive) "字幕已开启" else "字幕已关闭"
                    },
                ) {
                    AppIcon(
                        imageVector = if (subtitleActive) Icons.Filled.ClosedCaption
                            else Icons.Outlined.ClosedCaptionOff,
                        contentDescription = "字幕",
                        tint = if (subtitleActive) com.android.purebilibili.core.ui.AppContentColors.accent()
                            else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp),
                    )
                }
                PortraitPlaybackTextAction(
                    text = "${speed}×", description = "倍速", onClick = onSpeedClick,
                    modifier = speedModifier,
                )
                if (quality.isNotBlank()) {
                    PortraitPlaybackTextAction(
                        text = quality, description = "画质", onClick = onQualityClick,
                        modifier = qualityModifier,
                    )
                }
            }
        }
    }
    Layout(
        modifier = modifier,
        content = {
            Box { playback() }
            Box { tools() }
            if (onEnterLandscape != null) {
                // 竖屏流里返回键已负责退出；末端按钮改为直接切到横屏全屏。
                AppIconButton(
                    onClick = onEnterLandscape,
                    modifier = Modifier
                        .size(width = 40.dp, height = 48.dp)
                        .testTag("player_landscape_toggle"),
                ) {
                    AppIcon(
                        imageVector = Icons.Rounded.ScreenRotation,
                        contentDescription = "横屏全屏",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                AppIconButton(
                    onClick = onExitFullscreen,
                    modifier = Modifier
                        .size(width = 40.dp, height = 48.dp)
                        .testTag("player_fullscreen_toggle"),
                ) {
                    AppIcon(
                        imageVector = Icons.Outlined.FullscreenExit,
                        contentDescription = stringResource(R.string.player_exit_fullscreen),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val exitAction = measurables[2].measure(loose)
        val toolsEnd = constraints.maxWidth - exitAction.width
        val playbackGroup = measurables[0].measure(loose.copy(maxWidth = toolsEnd))
        val gap = 4.dp.roundToPx()
        // 退出按钮固定在末端，窄窗口只滚动中间的工具区。
        val toolsGroup = measurables[1].measure(loose.copy(
            maxWidth = (toolsEnd - playbackGroup.width - gap).coerceAtLeast(0),
        ))
        val height = constraints.constrainHeight(maxOf(playbackGroup.height, toolsGroup.height, exitAction.height))
        layout(constraints.maxWidth, height) {
            playbackGroup.placeRelative(0, (height - playbackGroup.height) / 2)
            toolsGroup.placeRelative(
                toolsEnd - toolsGroup.width,
                (height - toolsGroup.height) / 2,
            )
            exitAction.placeRelative(toolsEnd, (height - exitAction.height) / 2)
        }
    }
}

@Composable
private fun PortraitPlaybackTextAction(
    text: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 40.dp)
            .clickable(role = Role.Button, onClickLabel = description, onClick = appActionWithFeedback(onClick))
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            tapToCopyEnabled = false,
        )
    }
}
