package com.aistudio.netgate.ui.theme

import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.data.model.BlurEngineStyle
import com.aistudio.netgate.data.model.NetGateThemeStyle
import com.aistudio.netgate.data.model.ThemeMode

val LocalIsDarkTheme = staticCompositionLocalOf { true }
val LocalThemeStyle = staticCompositionLocalOf { NetGateThemeStyle.NETGATE_DEFAULT }
val LocalDynamicColorEnabled = staticCompositionLocalOf { true }

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldBright,
    onPrimary = Color(0xFF002114),
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = EmeraldBright,
    secondary = WifiAccent,
    onSecondary = Color(0xFF002638),
    secondaryContainer = Color(0xFF0C2D42),
    onSecondaryContainer = WifiAccent,
    tertiary = DataAccent,
    onTertiary = Color(0xFF1E103D),
    tertiaryContainer = Color(0xFF2B1C4E),
    onTertiaryContainer = DataAccent,
    error = BlockedRedBright,
    onError = Color(0xFF3B0609),
    errorContainer = BlockedRedContainerDark,
    onErrorContainer = BlockedRedBright,
    background = SlateBackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SlateSurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SlateSurfaceElevatedDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = Color(0xFF070B14),
    surfaceContainerLow = SlateSurfaceDark,
    surfaceContainer = SlateSurfaceElevatedDark,
    surfaceContainerHigh = SlateSurfaceHighestDark,
    outline = SlateOutlineDark,
    outlineVariant = Color(0xFF1E293B)
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = EmeraldDeep,
    secondary = WifiAccentDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF075985),
    tertiary = DataAccentDeep,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF4C1D95),
    error = BlockedRed,
    onError = Color.White,
    errorContainer = BlockedRedContainerLight,
    onErrorContainer = Color(0xFF991B1B),
    background = SlateBackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SlateSurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SlateSurfaceElevatedLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = SlateSurfaceElevatedLight,
    surfaceContainerHigh = SlateSurfaceHighestLight,
    outline = SlateOutlineLight,
    outlineVariant = Color(0xFFE2E8F0)
)

private val LiquidGlassDarkColorScheme = darkColorScheme(
    primary = LiquidGlassCyan,
    onPrimary = Color(0xFF001E2E),
    primaryContainer = Color(0xFF0B3554),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = LiquidGlassAqua,
    onSecondary = Color(0xFF00241E),
    secondaryContainer = Color(0xFF083933),
    onSecondaryContainer = Color(0xFF99F6E4),
    tertiary = LiquidGlassPrismViolet,
    onTertiary = Color(0xFF280B47),
    tertiaryContainer = Color(0xFF39195E),
    onTertiaryContainer = Color(0xFFE9D5FF),
    error = BlockedRedBright,
    onError = Color(0xFF3B0609),
    errorContainer = BlockedRedContainerDark,
    onErrorContainer = BlockedRedBright,
    background = LiquidGlassDarkBg,
    onBackground = Color(0xFFF8FAFC),
    surface = LiquidGlassDarkSurface,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF17243D),
    onSurfaceVariant = Color(0xFFAEC0D8),
    surfaceContainerLowest = Color(0xFF040812),
    surfaceContainerLow = Color(0xFF0D1528),
    surfaceContainer = Color(0xFF142038),
    surfaceContainerHigh = Color(0xFF1D2D4E),
    outline = Color(0xFF4C6A94),
    outlineVariant = Color(0xFF263858)
)

private val LiquidGlassLightColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EFFF),
    onPrimaryContainer = Color(0xFF073A5C),
    secondary = Color(0xFF0D9488),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF134E4A),
    tertiary = Color(0xFF8B5CF6),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF4C1D95),
    error = BlockedRed,
    onError = Color.White,
    errorContainer = BlockedRedContainerLight,
    onErrorContainer = Color(0xFF991B1B),
    background = LiquidGlassLightBg,
    onBackground = Color(0xFF0B1528),
    surface = LiquidGlassLightSurface,
    onSurface = Color(0xFF0B1528),
    surfaceVariant = Color(0xFFE1EEFF),
    onSurfaceVariant = Color(0xFF3E526E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F9FF),
    surfaceContainer = Color(0xFFE6F1FE),
    surfaceContainerHigh = Color(0xFFD6E7FC),
    outline = Color(0xFF94B2D6),
    outlineVariant = Color(0xFFC8DCF4)
)

private val ExpressiveDarkColorScheme = darkColorScheme(
    primary = ExpressiveTealDark,
    onPrimary = Color(0xFF002822),
    primaryContainer = Color(0xFF005147),
    onPrimaryContainer = Color(0xFF9EF7E8),
    secondary = ExpressiveCobaltDark,
    onSecondary = Color(0xFF0B2358),
    secondaryContainer = Color(0xFF1D3B82),
    onSecondaryContainer = Color(0xFFD7E3FF),
    tertiary = ExpressiveCoralDark,
    onTertiary = Color(0xFF471204),
    tertiaryContainer = Color(0xFF70240E),
    onTertiaryContainer = Color(0xFFFFDBD1),
    error = BlockedRedBright,
    onError = Color(0xFF3B0609),
    errorContainer = BlockedRedContainerDark,
    onErrorContainer = BlockedRedBright,
    background = ExpressiveDarkBg,
    onBackground = Color(0xFFF5F8FC),
    surface = ExpressiveDarkSurface,
    onSurface = Color(0xFFF5F8FC),
    surfaceVariant = Color(0xFF1E263D),
    onSurfaceVariant = Color(0xFFB4BED6),
    surfaceContainerLowest = Color(0xFF080B14),
    surfaceContainerLow = Color(0xFF121828),
    surfaceContainer = Color(0xFF1A2238),
    surfaceContainerHigh = Color(0xFF252F4D),
    outline = Color(0xFF47557A),
    outlineVariant = Color(0xFF2A3452)
)

private val ExpressiveLightColorScheme = lightColorScheme(
    primary = ExpressiveTealLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA7F3E6),
    onPrimaryContainer = Color(0xFF002620),
    secondary = ExpressiveCobaltLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE5FF),
    onSecondaryContainer = Color(0xFF0B2260),
    tertiary = ExpressiveCoralLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDED5),
    onTertiaryContainer = Color(0xFF420D00),
    error = BlockedRed,
    onError = Color.White,
    errorContainer = BlockedRedContainerLight,
    onErrorContainer = Color(0xFF991B1B),
    background = ExpressiveLightBg,
    onBackground = Color(0xFF0F171A),
    surface = Color.White,
    onSurface = Color(0xFF0F171A),
    surfaceVariant = Color(0xFFE3ECEA),
    onSurfaceVariant = Color(0xFF40504E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6FAFA),
    surfaceContainer = Color(0xFFE9F2F0),
    surfaceContainerHigh = Color(0xFFDCE9E6),
    outline = Color(0xFF8AA29D),
    outlineVariant = Color(0xFFCFE0DC)
)

private val YouLiteDarkColorScheme = darkColorScheme(
    primary = YouLiteSageDark,
    onPrimary = Color(0xFF0F2B1D),
    primaryContainer = Color(0xFF214533),
    onPrimaryContainer = Color(0xFFD0F2DF),
    secondary = YouLiteMistDark,
    onSecondary = Color(0xFF1E2D3B),
    secondaryContainer = Color(0xFF2E3F4F),
    onSecondaryContainer = Color(0xFFDCE7F2),
    tertiary = YouLiteSandDark,
    onTertiary = Color(0xFF33260E),
    tertiaryContainer = Color(0xFF4A3818),
    onTertiaryContainer = Color(0xFFFCE7BF),
    error = BlockedRedBright,
    onError = Color(0xFF3B0609),
    errorContainer = BlockedRedContainerDark,
    onErrorContainer = BlockedRedBright,
    background = YouLiteDarkBg,
    onBackground = Color(0xFFECF1ED),
    surface = YouLiteDarkSurface,
    onSurface = Color(0xFFECF1ED),
    surfaceVariant = Color(0xFF212824),
    onSurfaceVariant = Color(0xFFA7B4AC),
    surfaceContainerLowest = Color(0xFF0B0E0C),
    surfaceContainerLow = Color(0xFF141916),
    surfaceContainer = Color(0xFF1C231F),
    surfaceContainerHigh = Color(0xFF262F2A),
    outline = Color(0xFF4B5850),
    outlineVariant = Color(0xFF2B352F)
)

private val YouLiteLightColorScheme = lightColorScheme(
    primary = YouLiteSageLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EFE0),
    onPrimaryContainer = Color(0xFF0E301F),
    secondary = YouLiteMistLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE7F0),
    onSecondaryContainer = Color(0xFF1D3042),
    tertiary = YouLiteSandLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF6E7C9),
    onTertiaryContainer = Color(0xFF382707),
    error = BlockedRed,
    onError = Color.White,
    errorContainer = BlockedRedContainerLight,
    onErrorContainer = Color(0xFF991B1B),
    background = YouLiteLightBg,
    onBackground = Color(0xFF161D18),
    surface = Color.White,
    onSurface = Color(0xFF161D18),
    surfaceVariant = Color(0xFFE7EDE8),
    onSurfaceVariant = Color(0xFF4E5B52),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FBF8),
    surfaceContainer = Color(0xFFEDF3EE),
    surfaceContainerHigh = Color(0xFFE1EAE2),
    outline = Color(0xFFA4B2A8),
    outlineVariant = Color(0xFFD6E0D8)
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private fun shapesForStyle(style: NetGateThemeStyle): Shapes {
    return when (style) {
        NetGateThemeStyle.NETGATE_DEFAULT -> AppShapes
        NetGateThemeStyle.LIQUID_GLASS -> Shapes(
            extraSmall = RoundedCornerShape(12.dp),
            small = RoundedCornerShape(16.dp),
            medium = RoundedCornerShape(22.dp),
            large = RoundedCornerShape(26.dp),
            extraLarge = RoundedCornerShape(32.dp)
        )
        NetGateThemeStyle.MATERIAL_3_EXPRESSIVE -> Shapes(
            extraSmall = RoundedCornerShape(12.dp),
            small = RoundedCornerShape(18.dp),
            medium = RoundedCornerShape(24.dp),
            large = RoundedCornerShape(30.dp),
            extraLarge = RoundedCornerShape(36.dp)
        )
        NetGateThemeStyle.MATERIAL_YOU_LITE -> Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(26.dp)
        )
    }
}

@Composable
private fun animateColorSchemeCrossFade(
    target: ColorScheme,
    style: NetGateThemeStyle
): ColorScheme {
    val colorSpring = spring<Color>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = if (style == NetGateThemeStyle.MATERIAL_YOU_LITE) Spring.StiffnessMedium else Spring.StiffnessMediumLow
    )
    val primary by animateColorAsState(target.primary, colorSpring, label = "themePrimary")
    val onPrimary by animateColorAsState(target.onPrimary, colorSpring, label = "themeOnPrimary")
    val primaryContainer by animateColorAsState(target.primaryContainer, colorSpring, label = "themePrimaryContainer")
    val onPrimaryContainer by animateColorAsState(target.onPrimaryContainer, colorSpring, label = "themeOnPrimaryContainer")
    val secondary by animateColorAsState(target.secondary, colorSpring, label = "themeSecondary")
    val onSecondary by animateColorAsState(target.onSecondary, colorSpring, label = "themeOnSecondary")
    val secondaryContainer by animateColorAsState(target.secondaryContainer, colorSpring, label = "themeSecondaryContainer")
    val onSecondaryContainer by animateColorAsState(target.onSecondaryContainer, colorSpring, label = "themeOnSecondaryContainer")
    val tertiary by animateColorAsState(target.tertiary, colorSpring, label = "themeTertiary")
    val onTertiary by animateColorAsState(target.onTertiary, colorSpring, label = "themeOnTertiary")
    val tertiaryContainer by animateColorAsState(target.tertiaryContainer, colorSpring, label = "themeTertiaryContainer")
    val onTertiaryContainer by animateColorAsState(target.onTertiaryContainer, colorSpring, label = "themeOnTertiaryContainer")
    val error by animateColorAsState(target.error, colorSpring, label = "themeError")
    val onError by animateColorAsState(target.onError, colorSpring, label = "themeOnError")
    val errorContainer by animateColorAsState(target.errorContainer, colorSpring, label = "themeErrorContainer")
    val onErrorContainer by animateColorAsState(target.onErrorContainer, colorSpring, label = "themeOnErrorContainer")
    val background by animateColorAsState(target.background, colorSpring, label = "themeBackground")
    val onBackground by animateColorAsState(target.onBackground, colorSpring, label = "themeOnBackground")
    val surface by animateColorAsState(target.surface, colorSpring, label = "themeSurface")
    val onSurface by animateColorAsState(target.onSurface, colorSpring, label = "themeOnSurface")
    val surfaceVariant by animateColorAsState(target.surfaceVariant, colorSpring, label = "themeSurfaceVariant")
    val onSurfaceVariant by animateColorAsState(target.onSurfaceVariant, colorSpring, label = "themeOnSurfaceVariant")
    val surfaceContainerLowest by animateColorAsState(target.surfaceContainerLowest, colorSpring, label = "themeSurfaceContainerLowest")
    val surfaceContainerLow by animateColorAsState(target.surfaceContainerLow, colorSpring, label = "themeSurfaceContainerLow")
    val surfaceContainer by animateColorAsState(target.surfaceContainer, colorSpring, label = "themeSurfaceContainer")
    val surfaceContainerHigh by animateColorAsState(target.surfaceContainerHigh, colorSpring, label = "themeSurfaceContainerHigh")
    val surfaceContainerHighest by animateColorAsState(target.surfaceContainerHighest, colorSpring, label = "themeSurfaceContainerHighest")
    val outline by animateColorAsState(target.outline, colorSpring, label = "themeOutline")
    val outlineVariant by animateColorAsState(target.outlineVariant, colorSpring, label = "themeOutlineVariant")

    return target.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceContainerLowest = surfaceContainerLowest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        outline = outline,
        outlineVariant = outlineVariant
    )
}

@Composable
fun NetGateTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    themeStyle: NetGateThemeStyle = NetGateThemeStyle.NETGATE_DEFAULT,
    dynamicColor: Boolean = true,
    blurEngineStyle: BlurEngineStyle = BlurEngineStyle.ACRYLIC_PRO,
    blurIntensity: Float = 1.0f,
    blurAnimationEnabled: Boolean = true,
    lowPowerMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val liveNightModeBits = remember {
        mutableIntStateOf(configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
    }
    LaunchedEffect(configuration.uiMode) {
        val configBits = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (configBits != liveNightModeBits.intValue) {
            liveNightModeBits.intValue = configBits
        }
    }

    DisposableEffect(context.applicationContext) {
        val appContext = context.applicationContext
        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                val nextBits = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (nextBits != liveNightModeBits.intValue) {
                    liveNightModeBits.intValue = nextBits
                }
            }

            override fun onLowMemory() = Unit
        }
        appContext.registerComponentCallbacks(callbacks)
        onDispose {
            runCatching { appContext.unregisterComponentCallbacks(callbacks) }
        }
    }

    val composeSystemDark = isSystemInDarkTheme()
    val systemDark = when (liveNightModeBits.intValue) {
        Configuration.UI_MODE_NIGHT_YES -> true
        Configuration.UI_MODE_NIGHT_NO -> false
        else -> composeSystemDark
    }

    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val dynamicPalette = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        null
    }

    val targetColorScheme = when (themeStyle) {
        NetGateThemeStyle.LIQUID_GLASS -> {
            val base = if (darkTheme) LiquidGlassDarkColorScheme else LiquidGlassLightColorScheme
            if (dynamicPalette != null) {
                base.copy(
                    primary = dynamicPalette.primary,
                    onPrimary = dynamicPalette.onPrimary,
                    primaryContainer = dynamicPalette.primaryContainer,
                    onPrimaryContainer = dynamicPalette.onPrimaryContainer,
                    secondary = dynamicPalette.secondary,
                    onSecondary = dynamicPalette.onSecondary,
                    secondaryContainer = dynamicPalette.secondaryContainer,
                    onSecondaryContainer = dynamicPalette.onSecondaryContainer,
                    tertiary = dynamicPalette.tertiary,
                    onTertiary = dynamicPalette.onTertiary,
                    tertiaryContainer = dynamicPalette.tertiaryContainer,
                    onTertiaryContainer = dynamicPalette.onTertiaryContainer
                )
            } else {
                base
            }
        }
        NetGateThemeStyle.MATERIAL_3_EXPRESSIVE -> {
            dynamicPalette ?: if (darkTheme) ExpressiveDarkColorScheme else ExpressiveLightColorScheme
        }
        NetGateThemeStyle.MATERIAL_YOU_LITE -> {
            dynamicPalette ?: if (darkTheme) YouLiteDarkColorScheme else YouLiteLightColorScheme
        }
        NetGateThemeStyle.NETGATE_DEFAULT -> {
            dynamicPalette ?: if (darkTheme) DarkColorScheme else LightColorScheme
        }
    }

    val animatedColorScheme = animateColorSchemeCrossFade(targetColorScheme, themeStyle)
    val motionSpec = ExpressiveMotionSpec(themeStyle = themeStyle)

    CompositionLocalProvider(
        LocalIsDarkTheme provides darkTheme,
        LocalThemeStyle provides themeStyle,
        LocalDynamicColorEnabled provides (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S),
        LocalExpressiveMotion provides motionSpec,
        LocalPerformanceMode provides lowPowerMode,
        LocalBlurEngineConfig provides BlurEngineConfig(
            style = blurEngineStyle,
            intensity = blurIntensity.coerceIn(0.4f, 1.5f),
            animateMotion = blurAnimationEnabled,
            themeStyle = themeStyle,
            useDynamicColor = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        )
    ) {
        MaterialTheme(
            colorScheme = animatedColorScheme,
            typography = AppTypography,
            shapes = shapesForStyle(themeStyle),
            content = content
        )
    }
}
