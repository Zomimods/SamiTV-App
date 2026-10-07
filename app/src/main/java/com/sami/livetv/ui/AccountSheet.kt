package com.sami.livetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sami.livetv.ui.i18n.LocalLang
import com.sami.livetv.ui.i18n.t
import com.sami.livetv.ui.theme.LocalPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(vm: AppViewModel) {
    val p = LocalPalette.current
    ModalBottomSheet(onDismissRequest = { vm.accountOpen = false; vm.clearAuthError() }, containerColor = p.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(p.accent), contentAlignment = Alignment.Center) { Text("S", color = p.onAccent, fontWeight = FontWeight.Black) }
                Text(t("accountModalTitle"), color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
            if (vm.user == null) GuestView(vm) else ProfileView(vm)
        }
    }
}

@Composable
private fun GuestView(vm: AppViewModel) {
    val p = LocalPalette.current
    val lang = LocalLang.current
    var signUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }

    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.surface2).padding(4.dp)) {
        listOf(false to "emailSignInBtn", true to "emailSignUpBtn").forEach { (mode, key) ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (signUp == mode) p.accent else p.surface2)
                    .clickable { signUp = mode; vm.clearAuthError() }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(t(key), color = if (signUp == mode) p.onAccent else p.text2, fontWeight = FontWeight.Black, fontSize = 13.sp) }
        }
    }
    Text(
        if (lang == com.sami.livetv.data.preferences.AppLanguage.English) "Sign in to watch channels — your favorites and watch history are saved and synced across your devices."
        else "سجّل الدخول لمشاهدة القنوات، وستُحفظ مفضلتك وسجل مشاهدتك وتُزامن تلقائيًا بين أجهزتك.",
        color = p.text2, fontSize = 12.sp, lineHeight = 19.sp,
    )
    if (signUp) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AuthField(t("firstNameLabel"), first, { first = it }, Modifier.weight(1f))
            AuthField(t("lastNameLabel"), last, { last = it }, Modifier.weight(1f))
        }
        AuthField(t("ageLabel"), age, { age = it.filter(Char::isDigit).take(3) }, keyboard = KeyboardType.Number)
    }
    AuthField(t("emailPlaceholder"), email, { email = it }, keyboard = KeyboardType.Email)
    AuthField(t("passwordPlaceholder"), pass, { pass = it }, keyboard = KeyboardType.Password, password = true)
    if (signUp) AuthField(if (lang == com.sami.livetv.data.preferences.AppLanguage.English) "Confirm password" else "تأكيد كلمة المرور", confirm, { confirm = it }, keyboard = KeyboardType.Password, password = true)
    if (vm.authError.isNotBlank()) Text(vm.authError, color = p.bad, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Box(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(if (vm.authBusy) p.accent.copy(alpha = .5f) else p.accent)
            .clickable(enabled = !vm.authBusy) { vm.submitAuth(signUp, email, pass, confirm, first, last, age) },
        contentAlignment = Alignment.Center,
    ) { Text(t(if (signUp) "emailSignUpBtn" else "emailSignInBtn"), color = p.onAccent, fontWeight = FontWeight.Black, fontSize = 14.sp) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(t(if (signUp) "emailToggleToSignin" else "emailToggleToSignup"), color = p.accent2, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { signUp = !signUp; vm.clearAuthError() })
        Text(t("emailForgotPassword"), color = p.accent2, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { vm.resetPassword(email) })
    }
    Text(t("accountSyncNote"), color = p.text3, fontSize = 11.sp)
}

@Composable
private fun ProfileView(vm: AppViewModel) {
    val p = LocalPalette.current
    val u = vm.user ?: return
    val name = u.displayName ?: vm.profile?.name ?: ""
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(58.dp).clip(CircleShape).background(p.accentSoft).border(1.dp, p.accentLine, CircleShape), contentAlignment = Alignment.Center) {
            val photo = vm.profile?.photo
            if (photo != null && photo.startsWith("https")) SamiImage(photo, Modifier.fillMaxSize(), fallback = { Text(name.take(1).uppercase(), color = p.accent2, fontSize = 22.sp, fontWeight = FontWeight.Black) })
            else Text(name.take(1).uppercase(), color = p.accent2, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }
        Column {
            Text(name.ifBlank { "—" }, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text(u.email ?: "", color = p.text3, fontSize = 12.sp)
            vm.profile?.age?.let { Text(t("accountAgeText") + ": " + it, color = p.text3, fontSize = 11.sp) }
        }
    }
    Text(
        when { vm.cloudSynced -> t("accountSyncedNow"); vm.syncFailed -> t("accountSyncError"); else -> t("accountSyncingNow") },
        color = p.text2, fontSize = 12.sp,
    )
    Box(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, p.bad.copy(alpha = .5f), RoundedCornerShape(14.dp))
            .clickable { vm.signOut(); vm.accountOpen = false; vm.showToast(com.sami.livetv.ui.i18n.I18n.t("accountSignedOutToast", vm.currentLang)) },
        contentAlignment = Alignment.Center,
    ) { Text(t("accountSignOut"), color = p.bad, fontWeight = FontWeight.Black, fontSize = 13.sp) }
}

@Composable
private fun AuthField(
    label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text, password: Boolean = false,
) {
    val p = LocalPalette.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = p.text3, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(color = p.text, fontSize = 14.sp), cursorBrush = SolidColor(p.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).background(p.surface2).border(1.dp, p.line, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 13.dp),
        )
    }
}
