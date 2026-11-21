package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.UserRole
import com.example.data.prefs.AppThemeStyle
import com.example.data.repository.AuthenticatedUser
import com.example.ui.components.PremiumConfirmationDialog
import com.example.ui.components.PremiumGlassCard
import com.example.ui.components.PremiumSectionHeader
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DangerSoftBg
import com.example.ui.theme.LocalLiquidGlassColors
import com.example.ui.theme.PremiumTeal
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessSoftBg
import com.example.ui.theme.VioletAccent
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.SessionUiState
import com.example.ui.viewmodel.SettingsUiState
import com.example.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
  user: AuthenticatedUser,
  sessionState: SessionUiState,
  settingsViewModel: SettingsViewModel,
  settingsState: SettingsUiState,
  onSetDarkMode: (Boolean) -> Unit,
  onSetThemeStyle: (AppThemeStyle) -> Unit,
  onLogout: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)

  val glass = LocalLiquidGlassColors.current
  val isSuper = user.role == UserRole.SUPER_ADMIN
  val canBackup = user.permissions.backupDatabase || isSuper
  val canRestore = user.permissions.restoreDatabase || isSuper

  var confirmBackup by remember { mutableStateOf(false) }
  var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
  var confirmLogout by remember { mutableStateOf(false) }

  val backupLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
      if (uri != null) settingsViewModel.createBackup(user, uri)
    }

  val restoreLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) pendingRestoreUri = uri
    }

  val csvExportLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
      if (uri != null) settingsViewModel.exportCsv(user, uri)
    }

  Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
        onClick = onBack,
        modifier =
          Modifier.clip(RoundedCornerShape(14.dp))
            .background(glass.glassSurfaceElevated)
            .border(1.dp, glass.glassBorderTop, RoundedCornerShape(14.dp)),
      ) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "رجوع")
      }
      Spacer(Modifier.width(12.dp))
      Text(
        text = "الإعدادات",
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
      )
    }

    Column(
      modifier =
        Modifier.weight(1f)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      AnimatedVisibility(visible = !settingsState.feedbackMessage.isNullOrBlank()) {
        Surface(
          color = if (settingsState.isError) DangerSoftBg else SuccessSoftBg,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().clickable { settingsViewModel.clearFeedback() },
        ) {
          Text(
            text = settingsState.feedbackMessage.orEmpty(),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = if (settingsState.isError) DangerRed else SuccessGreen,
            modifier = Modifier.padding(12.dp),
          )
        }
      }

      // Appearance Group
      PremiumSectionHeader(title = "المظهر")
      PremiumGlassCard(contentPadding = PaddingValues(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector =
                if (sessionState.isDarkMode) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
              contentDescription = null,
              tint = WarningAmber,
            )
            Spacer(Modifier.width(12.dp))
            Column {
              Text(
                text = if (sessionState.isDarkMode) "الوضع الداكن" else "الوضع الفاتح",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
              )
              Text(
                text = "تبديل مظهر التطبيق الفاتح والداكن",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Switch(
            checked = sessionState.isDarkMode,
            onCheckedChange = onSetDarkMode,
            modifier = Modifier.testTag("dark_mode_switch"),
          )
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Rounded.Palette, contentDescription = null, tint = PrimaryBlue)
          Spacer(Modifier.width(10.dp))
          Text("نمط الألوان", style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          AppThemeStyle.entries.forEach { style ->
            FilterChip(
              selected = sessionState.themeStyle == style,
              onClick = { onSetThemeStyle(style) },
              label = { Text(style.arabicName) },
            )
          }
        }
      }

      // Language Group
      PremiumSectionHeader(title = "اللغة")
      PremiumGlassCard(contentPadding = PaddingValues(14.dp)) {
        SettingsRow(
          icon = Icons.Rounded.Language,
          iconColor = PrimaryBlue,
          title = "اللغة",
          subtitle = "العربية (RTL الافتراضية)",
          onClick = null,
        )
      }

      // Data Management Group
      PremiumSectionHeader(title = "إدارة البيانات")
      PremiumGlassCard(contentPadding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          SettingsRow(
            icon = Icons.Rounded.Backup,
            iconColor = PrimaryBlue,
            title = "النسخ الاحتياطي لقاعدة البيانات",
            subtitle =
              if (canBackup) "حفظ نسخة كاملة بصيغة JSON شاملة الشعار والبيانات"
              else "مقيد بالصلاحيات",
            onClick = if (canBackup) { { confirmBackup = true } } else null,
          )
          SettingsRow(
            icon = Icons.Rounded.Restore,
            iconColor = PremiumTeal,
            title = "استعادة قاعدة البيانات",
            subtitle =
              if (canRestore) "استيراد نسخة احتياطية محفوظة مسبقاً"
              else "مقيد بالصلاحيات",
            onClick =
              if (canRestore) {
                { restoreLauncher.launch(arrayOf("application/json", "*/*")) }
              } else null,
          )
          SettingsRow(
            icon = Icons.Rounded.Download,
            iconColor = VioletAccent,
            title = "تصدير البيانات (CSV)",
            subtitle = "تصدير الأصناف وعروض الأسعار لفتحها في Excel",
            onClick = { csvExportLauncher.launch("munakasat_export.csv") },
          )
          val totalKb =
            (settingsState.storageStats.databaseSizeBytes +
              settingsState.storageStats.cacheSizeBytes +
              settingsState.storageStats.logosSizeBytes) / 1024
          SettingsRow(
            icon = Icons.Rounded.Storage,
            iconColor = WarningAmber,
            title = "إدارة التخزين",
            subtitle = "المساحة المستخدمة: ${totalKb} كيلوبايت (قاعدة البيانات والشعارات)",
            onClick = null,
          )
          SettingsRow(
            icon = Icons.Rounded.CleaningServices,
            iconColor = SuccessGreen,
            title = "تنظيف الملفات المؤقتة",
            subtitle =
              "حجم المؤقتات: ${settingsState.storageStats.cacheSizeBytes / 1024} كيلوبايت",
            onClick = { settingsViewModel.clearCache() },
          )
        }
      }

      // Application Group
      PremiumSectionHeader(title = "التطبيق")
      PremiumGlassCard(contentPadding = PaddingValues(14.dp)) {
        SettingsRow(
          icon = Icons.Rounded.Info,
          iconColor = PrimaryBlue,
          title = "معلومات التطبيق",
          subtitle = "نظام المناقصات — الإصدار v1.0.0 — إدارة احترافية لعروض الأسعار",
          onClick = null,
        )
      }

      // Account Group
      PremiumSectionHeader(title = "الحساب")
      PremiumGlassCard(contentPadding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          SettingsRow(
            icon = Icons.Rounded.Person,
            iconColor = PrimaryBlue,
            title = user.entity.fullName,
            subtitle = "@${user.entity.username}  •  ${user.role.arabicLabel}",
            onClick = null,
          )
          SettingsRow(
            icon = Icons.AutoMirrored.Rounded.Logout,
            iconColor = DangerRed,
            title = "تسجيل الخروج",
            subtitle = "إنهاء الجلسة الحالية والعودة لشاشة الدخول",
            titleColor = DangerRed,
            onClick = { confirmLogout = true },
            modifier = Modifier.testTag("logout_button"),
          )
        }
      }
      Spacer(Modifier.height(16.dp))
    }
  }

  PremiumConfirmationDialog(
    visible = confirmBackup,
    title = "تأكيد النسخ الاحتياطي",
    message = "سيتم تصدير نسخة احتياطية كاملة تشمل جميع المستخدمين والأصناف وعروض الأسعار وسجل العمليات والشعار.",
    confirmText = "متابعة",
    isDestructive = false,
    onConfirm = {
      confirmBackup = false
      backupLauncher.launch("munakasat_backup_${System.currentTimeMillis()}.json")
    },
    onDismiss = { confirmBackup = false },
  )

  PremiumConfirmationDialog(
    visible = pendingRestoreUri != null,
    title = "تأكيد استعادة النسخة الاحتياطية",
    message = "تحذير: ستستبدل عملية الاستعادة جميع البيانات الحالية ببيانات النسخة المختارة. هل تريد المتابعة؟",
    confirmText = "استعادة",
    isDestructive = true,
    onConfirm = {
      val uri = pendingRestoreUri
      pendingRestoreUri = null
      if (uri != null) settingsViewModel.restoreBackup(user, uri)
    },
    onDismiss = { pendingRestoreUri = null },
  )

  PremiumConfirmationDialog(
    visible = confirmLogout,
    title = "تسجيل الخروج",
    message = "هل تريد تسجيل الخروج من الحساب الحالي؟",
    confirmText = "تسجيل الخروج",
    isDestructive = true,
    onConfirm = {
      confirmLogout = false
      onLogout()
    },
    onDismiss = { confirmLogout = false },
  )
}

@Composable
private fun SettingsRow(
  icon: ImageVector,
  iconColor: Color,
  title: String,
  subtitle: String,
  onClick: (() -> Unit)?,
  modifier: Modifier = Modifier,
  titleColor: Color = MaterialTheme.colorScheme.onSurface,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier =
        Modifier.size(40.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(iconColor.copy(alpha = 0.14f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = iconColor,
        modifier = Modifier.size(21.dp),
      )
    }
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        color = titleColor,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (onClick != null) {
      Icon(
        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
