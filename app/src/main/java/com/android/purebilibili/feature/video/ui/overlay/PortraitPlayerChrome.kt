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
import androidx.compose.material.icons.outlined.People
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
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

/** 竖屏顶栏返回键旁的「N人正在看」。 */
@Composable
internal fun PortraitOnlineCountLabel(onlineCount: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(start = 4.dp, end = 8.dp).widthIn(max = 150.dp).testTag("portrait_online_count"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AppIcon(
            imageVector = Icons.Outlined.People,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(16.dp),
        )
        AppText(
            text = onlineCount,
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 13.sp,
            lineHeight = 17.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            tapToCopyEnabled = false,
        )
    }
}

/** 标题前缀（合集 · 第 N 集 / 分 P）：点按打开对应列表，标题其余部分点按展开。 */
private const val PORTRAIT_TITLE_PREFIX_TAG = "portrait_title_prefix"

/**
 * 竖屏全屏的信息区（参考官方竖屏布局）；展开状态只属于当前稿件与标题。
 * 自上而下：UP 主（头像 + 名称 + 粉丝数 + 关注）→「合集 · 第 N 集 | 标题」+ 播放量。
 * 播放量固定在标题右侧单独排版，长标题只会省略标题本身，不会截掉播放量。
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
    playCountText: String? = null,
    authorSubtitle: String? = null,
) {
    var expanded by remember(bvid, title) { mutableStateOf(false) }
    var titleOverflows by remember(bvid, title) { mutableStateOf(false) }
    val accent = AppContentColors.accent()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (authorName.isNotBlank()) {
            PortraitMetadataAuthorRow(
                authorKey = bvid,
                authorName = authorName,
                authorFace = authorFace,
                subtitle = authorSubtitle,
                isFollowing = isFollowing,
                followStatusKnown = followStatusKnown,
                onFollowClick = onFollowClick,
                onAuthorClick = onAuthorClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val prefix = collectionLabel ?: episodeLabel
        val onPrefixClick = if (collectionLabel != null) onCollectionClick else onEpisodeClick
        val latestPrefixClick by rememberUpdatedState(onPrefixClick)
        if (title.isNotBlank() || !prefix.isNullOrBlank() || !playCountText.isNullOrBlank()) {
            val canToggle = expanded || titleOverflows
            val annotatedTitle = remember(prefix, title, accent) {
                buildAnnotatedString {
                    if (!prefix.isNullOrBlank()) {
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = PORTRAIT_TITLE_PREFIX_TAG,
                                linkInteractionListener = { latestPrefixClick() },
                            ),
                        ) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                                append(prefix)
                            }
                        }
                        withStyle(SpanStyle(color = Color.White.copy(alpha = 0.5f))) { append("  |  ") }
                    }
                    append(title)
                }
            }
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
                    text = annotatedTitle,
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (expanded) 6 else 2,
                    overflow = TextOverflow.Ellipsis,
                    tapToCopyEnabled = false,
                    onTextLayout = { if (!expanded) titleOverflows = it.hasVisualOverflow },
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!playCountText.isNullOrBlank()) {
                    AppText(
                        text = playCountText,
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 13.sp,
                        lineHeight = 21.sp,
                        maxLines = 1,
                        softWrap = false,
                        tapToCopyEnabled = false,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .testTag("portrait_player_stats"),
                    )
                }
                if (canToggle) {
                    AppIcon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(start = 2.dp, bottom = 1.dp).size(20.dp),
                    )
                }
            }
        }
    }
}

/** 头像 + 名称 + 粉丝数，右侧为实心关注按钮；取关仍走确认流程。 */
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
                .size(44.dp)
                .clip(CircleShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
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
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
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
                    )
                }
            }
        }
        val accent = AppContentColors.accent()
        Box(
            modifier = Modifier
                .followActionAnchor()
                .height(32.dp)
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
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            AppText(
                text = when {
                    !followStatusKnown -> "待确认"
                    isFollowing -> "已关注"
                    else -> "+ 关注"
                },
                color = if (following) Color.White.copy(alpha = 0.82f) else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                tapToCopyEnabled = false,
            )
        }
    }
}

/** 底栏画质文案只保留清晰度本身（「720P 准高清」→「720P」）。 */
internal fun compactPortraitQualityLabel(label: String): String {
    val trimmed = label.trim()
    val head = trimmed.substringBefore(' ')
    return if (head.isNotEmpty() && head.first().isDigit()) head else trimmed
}

/** 倍速为 1.0 时显示「正常」，其余显示具体倍率。 */
internal fun portraitSpeedLabel(speed: Float): String =
    if (speed == 1f) "正常" else "${speed}×"

/** 竖屏播放量文案：「16.9万播放」；未知或为 0 时返回 null。 */
internal fun resolvePortraitPlayCountLabel(playCountText: String): String? =
    playCountText.trim().takeIf { it.isNotEmpty() && it != "0" && it != "-" }?.let { "${it}播放" }

/**
 * 竖屏底栏（参考官方竖屏）：发弹幕胶囊（内含弹幕开关）· 详情页 · 全屏。
 * 播放 / 暂停、倍速、画质、字幕等其余播控收进「更多」菜单；点按画面中央也可暂停。
 */
@Composable
internal fun PortraitBottomActions(
    danmakuEnabled: Boolean,
    onDanmakuToggle: () -> Unit,
    onSendDanmakuClick: () -> Unit,
    onOpenDetails: (() -> Unit)?,
    onExitFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    onEnterLandscape: (() -> Unit)? = null,
) {
    val accent = AppContentColors.accent()
    Row(
        modifier = modifier.heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PortraitDanmakuPill(
            enabled = danmakuEnabled,
            onToggle = onDanmakuToggle,
            onSend = onSendDanmakuClick,
            accent = accent,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        if (onOpenDetails != null) {
            PortraitBottomIconAction(
                icon = Icons.Outlined.Newspaper,
                label = "详情页",
                onClick = onOpenDetails,
                modifier = Modifier.testTag("player_open_details"),
            )
        }
        if (onEnterLandscape != null) {
            // 横屏比例视频：直接切到横屏全屏。
            PortraitBottomIconAction(
                icon = Icons.Rounded.ScreenRotation,
                label = "全屏",
                onClick = onEnterLandscape,
                modifier = Modifier.testTag("player_landscape_toggle"),
            )
        } else {
            PortraitBottomIconAction(
                icon = Icons.Outlined.FullscreenExit,
                label = stringResource(R.string.player_exit_fullscreen),
                onClick = onExitFullscreen,
                showLabel = false,
                modifier = Modifier.testTag("player_fullscreen_toggle"),
            )
        }
    }
}

/** 底栏图标按钮：图标 + 小字标签，固定 52dp 槽宽，满足 48dp 触控高度。 */
@Composable
private fun PortraitBottomIconAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    Column(
        modifier = modifier
            .size(width = 52.dp, height = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = appActionWithFeedback(onClick)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppIcon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
        if (showLabel) {
            AppText(
                text = label,
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 10.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                softWrap = false,
                tapToCopyEnabled = false,
            )
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
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.16f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag("player_send_danmaku")
                .clickable(role = Role.Button, onClickLabel = "发弹幕", onClick = appActionWithFeedback(onSend))
                .padding(start = 16.dp, end = 6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            AppText(
                text = "发弹幕",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                tapToCopyEnabled = false,
            )
        }
        Box(
            Modifier
                .width(1.dp)
                .height(16.dp)
                .background(Color.White.copy(alpha = 0.24f)),
        )
        NativeDanmakuToggleButton(
            enabled = enabled,
            onToggle = onToggle,
            activeTint = Color.White,
            inactiveTint = Color.White.copy(alpha = 0.55f),
            iconSize = 22.dp,
            modifier = Modifier
                .width(52.dp)
                .fillMaxHeight()
                .testTag("player_danmaku_toggle"),
        )
    }
}

/** 拖动细进度条时显示在进度条上方的「当前 / 总时长」。 */
@Composable
internal fun PortraitScrubTimeLabel(
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    modifier: Modifier = Modifier,
) {
    val seconds by remember(positionProvider) { derivedStateOf { positionProvider() / 1000L } }
    val durationSeconds by remember(durationProvider) { derivedStateOf { durationProvider() / 1000L } }
    val position = FormatUtils.formatDuration(seconds.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
    val duration = FormatUtils.formatDuration(durationSeconds.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
    AppText(
        text = "$position / $duration",
        color = Color.White,
        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
        maxLines = 1,
        softWrap = false,
        tapToCopyEnabled = false,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .testTag("player_time_viewport"),
    )
}

/** 标题前缀「合集 · 第 N 集」；当前视频不在合集列表里时只显示「合集」，没有合集返回 null。 */
internal fun resolvePortraitCollectionPrefix(
    season: com.android.purebilibili.data.model.response.UgcSeason?,
    bvid: String,
): String? {
    val episodes = season?.sections.orEmpty().flatMap { it.episodes }
    if (episodes.isEmpty()) return null
    val index = if (bvid.isBlank()) -1 else episodes.indexOfFirst { it.bvid == bvid }
    return if (index >= 0) "合集 · 第${index + 1}集" else "合集"
}
