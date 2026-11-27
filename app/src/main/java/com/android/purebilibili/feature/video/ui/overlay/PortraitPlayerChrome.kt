package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ClosedCaptionOff
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.rememberFollowActionWithConfirmation
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.feature.video.ui.components.NativeDanmakuToggleButton
import com.android.purebilibili.feature.video.ui.feedback.followActionAnchor
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

/** 竖屏信息区里可点进的入口（分 P、合集）。 */
private class PortraitMetadataLink(val label: String, val clickLabel: String, val onClick: () -> Unit)

/**
 * 竖屏全屏的信息区独立于顶部导航；展开状态只属于当前稿件与标题。
 * 布局自上而下：UP 主（头像 + 名称 + 播放量/在看人数 + 关注）→ 标题（点按展开）→ 合集 / 分 P / 视频详情入口。
 */
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (authorName.isNotBlank()) {
            PortraitMetadataAuthorRow(
                authorKey = bvid,
                authorName = authorName,
                authorFace = authorFace,
                subtitle = statsText,
                isFollowing = isFollowing,
                followStatusKnown = followStatusKnown,
                onFollowClick = onFollowClick,
                onAuthorClick = onAuthorClick,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (!statsText.isNullOrBlank()) {
            AppText(
                text = statsText,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                tapToCopyEnabled = false,
                modifier = Modifier.fillMaxWidth().testTag("portrait_player_stats"),
            )
        }
        if (title.isNotBlank()) {
            val canToggle = expanded || titleOverflows
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { stateDescription = if (expanded) "标题已展开" else "标题已收起" }
                    .clickable(
                        enabled = canToggle,
                        role = Role.Button,
                        onClickLabel = if (expanded) "收起标题" else "展开标题",
                        onClick = { expanded = !expanded },
                    ),
                verticalAlignment = Alignment.Bottom,
            ) {
                AppText(
                    text = title,
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (expanded) 6 else 2,
                    overflow = TextOverflow.Ellipsis,
                    tapToCopyEnabled = false,
                    onTextLayout = { if (!expanded) titleOverflows = it.hasVisualOverflow },
                    modifier = Modifier.weight(1f),
                )
                if (canToggle) {
                    AppIcon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(start = 4.dp, bottom = 1.dp).size(20.dp),
                    )
                }
            }
        }
        val links = listOfNotNull(
            episodeLabel?.let { PortraitMetadataLink(it, "打开选集", onEpisodeClick) },
            collectionLabel?.let { PortraitMetadataLink(it, "打开合集", onCollectionClick) },
        )
        if (links.isNotEmpty() || onOpenDetails != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                links.forEach { link ->
                    PortraitMetadataChip(
                        icon = Icons.Outlined.VideoLibrary,
                        label = link.label,
                        clickLabel = link.clickLabel,
                        onClick = link.onClick,
                        fillLabel = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (onOpenDetails != null) {
                    PortraitMetadataChip(
                        icon = Icons.Outlined.Newspaper,
                        label = "视频详情",
                        clickLabel = "打开视频详情",
                        onClick = onOpenDetails,
                        fillLabel = false,
                    )
                }
            }
        }
    }
}

/** 头像 + 名称 + 播放量/在看人数，右侧为实心关注按钮；取关仍走确认流程。 */
@Composable
private fun PortraitMetadataAuthorRow(
    authorKey: String,
    authorName: String,
    authorFace: String,
    subtitle: String?,
    isFollowing: Boolean,
    followStatusKnown: Boolean,
    onFollowClick: () -> Unit,
    onAuthorClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val following = followStatusKnown && isFollowing
    val followAction = rememberFollowActionWithConfirmation(
        targetKey = authorKey,
        userName = authorName,
        isFollowing = following,
        onFollowClick = onFollowClick,
    )
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false).then(
                if (onAuthorClick != null) Modifier.clickable(
                    role = Role.Button,
                    onClickLabel = "查看 $authorName 的资料",
                    onClick = onAuthorClick,
                ) else Modifier,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val avatarModifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.72f), CircleShape)
            if (authorFace.isNotBlank()) {
                AsyncImage(
                    model = FormatUtils.fixImageUrl(authorFace),
                    contentDescription = "$authorName 的头像",
                    contentScale = ContentScale.Crop,
                    modifier = avatarModifier,
                )
            } else {
                AppIcon(
                    imageVector = Icons.Outlined.AccountCircle,
                    contentDescription = "$authorName 的头像",
                    tint = Color.White,
                    modifier = avatarModifier,
                )
            }
            Column(modifier = Modifier.weight(1f, fill = false)) {
                AppText(
                    text = authorName,
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    tapToCopyEnabled = false,
                )
                if (!subtitle.isNullOrBlank()) {
                    AppText(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        tapToCopyEnabled = false,
                        modifier = Modifier.testTag("portrait_player_stats"),
                    )
                }
            }
        }
        val accent = AppContentColors.accent()
        Box(
            modifier = Modifier
                .followActionAnchor()
                .height(30.dp)
                .clip(RoundedCornerShape(50))
                .background(if (followStatusKnown && !isFollowing) accent else Color.White.copy(alpha = 0.16f))
                .semantics { selected = following }
                .clickable(
                    role = Role.Button,
                    onClickLabel = when {
                        !followStatusKnown -> "重新确认关注状态"
                        isFollowing -> "已关注，点击取消关注"
                        else -> "关注 $authorName"
                    },
                    onClick = appActionWithFeedback(followAction),
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            AppText(
                text = when {
                    !followStatusKnown -> "待确认"
                    isFollowing -> "已关注"
                    else -> "+ 关注"
                },
                color = if (following) Color.White.copy(alpha = 0.82f) else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                tapToCopyEnabled = false,
            )
        }
    }
}

/** 半透明胶囊入口：图标 + 文案 + 箭头；[fillLabel] 为 true 时箭头贴右端。 */
@Composable
private fun PortraitMetadataChip(
    icon: ImageVector,
    label: String,
    clickLabel: String,
    onClick: () -> Unit,
    fillLabel: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(role = Role.Button, onClickLabel = clickLabel, onClick = appActionWithFeedback(onClick))
            .padding(start = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AppIcon(icon, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(16.dp))
        AppText(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            tapToCopyEnabled = false,
            modifier = Modifier.weight(1f, fill = fillLabel),
        )
        AppIcon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 底栏画质文案只保留清晰度本身（「720P 准高清」→「720P」），避免挤占弹幕输入框。 */
internal fun compactPortraitQualityLabel(label: String): String {
    val trimmed = label.trim()
    val head = trimmed.substringBefore(' ')
    return if (head.isNotEmpty() && head.first().isDigit()) head else trimmed
}

/** 倍速为 1.0 时显示「倍速」，其余显示具体倍率。 */
internal fun portraitSpeedLabel(speed: Float): String =
    if (speed == 1f) "倍速" else "${speed}×"

/**
 * 竖屏进度行：当前时间 —— 进度条 —— 总时长。
 * 时间两侧使用等宽数字并按总时长预留宽度，播放过程中进度条不会左右跳动。
 */
@Composable
internal fun PortraitProgressRow(
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    modifier: Modifier = Modifier,
    progressBar: @Composable BoxScope.() -> Unit,
) {
    val seconds by remember(positionProvider) { derivedStateOf { positionProvider() / 1000L } }
    val durationSeconds by remember(durationProvider) { derivedStateOf { durationProvider() / 1000L } }
    val position = FormatUtils.formatDuration(seconds.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
    val duration = FormatUtils.formatDuration(durationSeconds.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
    val style = remember {
        TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val longest = if (position.length > duration.length) position else duration
    val labelWidth = remember(longest.length, duration, measurer, density) {
        with(density) { measurer.measure(longest, style).size.width.toDp() + 2.dp }
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        AppText(
            text = position,
            color = Color.White,
            style = style,
            textAlign = TextAlign.Start,
            maxLines = 1,
            softWrap = false,
            tapToCopyEnabled = false,
            modifier = Modifier.width(labelWidth).testTag("player_time_viewport"),
        )
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 8.dp),
            content = progressBar,
        )
        AppText(
            text = duration,
            color = Color.White.copy(alpha = 0.7f),
            style = style,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            tapToCopyEnabled = false,
            modifier = Modifier.width(labelWidth),
        )
    }
}

/**
 * 竖屏底部操作行：播放/暂停 · 弹幕输入框（内含弹幕开关）· 字幕 · 倍速 · 画质 · 横屏（或退出）。
 * 输入框占据剩余宽度，其余按钮固定槽宽，窄屏上优先压缩输入框而不是裁掉按钮。
 */
@Composable
internal fun PortraitPlaybackControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
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
    val subtitleActive = subtitleControlState.trackAvailable && subtitleControlState.enabled
    val accent = AppContentColors.accent()
    Row(modifier = modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 48.dp)
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
                modifier = Modifier.size(26.dp),
            )
        }
        PortraitDanmakuPill(
            enabled = danmakuEnabled,
            onToggle = onDanmakuToggle,
            onSend = onSendDanmakuClick,
            accent = accent,
            modifier = Modifier.weight(1f).padding(start = 4.dp, end = 4.dp),
        )
        // 字幕入口常驻；轨道未就绪或不存在时由菜单说明，避免底栏入口忽隐忽现。
        AppIconButton(
            onClick = onSubtitleClick,
            modifier = Modifier.size(width = 36.dp, height = 48.dp).then(subtitleModifier).semantics {
                stateDescription = if (subtitleActive) "字幕已开启" else "字幕已关闭"
            },
        ) {
            AppIcon(
                imageVector = if (subtitleActive) Icons.Filled.ClosedCaption else Icons.Outlined.ClosedCaptionOff,
                contentDescription = "字幕",
                tint = if (subtitleActive) accent else Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(22.dp),
            )
        }
        PortraitPlaybackTextAction(
            text = portraitSpeedLabel(speed), description = "倍速", onClick = onSpeedClick,
            modifier = speedModifier,
        )
        if (quality.isNotBlank()) {
            PortraitPlaybackTextAction(
                text = compactPortraitQualityLabel(quality), description = "画质：$quality", onClick = onQualityClick,
                modifier = qualityModifier,
            )
        }
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
    }
}

/** 胶囊弹幕输入框：左侧点按发弹幕，右侧为弹幕开关。 */
@Composable
private fun PortraitDanmakuPill(
    enabled: Boolean,
    onToggle: () -> Unit,
    onSend: () -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.14f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag("player_send_danmaku")
                .clickable(role = Role.Button, onClickLabel = "发弹幕", onClick = appActionWithFeedback(onSend))
                .padding(start = 14.dp, end = 6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            AppText(
                text = "发弹幕",
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 13.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                tapToCopyEnabled = false,
            )
        }
        Box(
            Modifier
                .width(1.dp)
                .height(14.dp)
                .background(Color.White.copy(alpha = 0.24f)),
        )
        NativeDanmakuToggleButton(
            enabled = enabled,
            onToggle = onToggle,
            activeTint = accent,
            inactiveTint = Color.White.copy(alpha = 0.6f),
            iconSize = 20.dp,
            modifier = Modifier
                .width(44.dp)
                .fillMaxHeight()
                .testTag("player_danmaku_toggle"),
        )
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
            .widthIn(min = 40.dp, max = 64.dp)
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
            overflow = TextOverflow.Ellipsis,
            tapToCopyEnabled = false,
        )
    }
}
