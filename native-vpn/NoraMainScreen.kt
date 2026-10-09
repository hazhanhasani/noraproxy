package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

private val bg = Color(0xFF101820)
private val surface = Color(0xFF202D36)
private val amber = Color(0xFFFEC482)
private val mint = Color(0xFF81E3B9)
private val muted = Color(0xFF9DAFB9)
private val white = Color(0xFFF3F6F7)

/** Replaces v2rayNG's customer home while retaining its actual Xray/VpnService. */
@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val state by mainViewModel.uiState.collectAsStateWithLifecycle()
    val loading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    var subscription by remember { mutableStateOf("") }
    var auto by remember { mutableStateOf(true) }
    var chosenCountry by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.groups, state.isTesting, loading) { tick++ }
    LaunchedEffect(Unit) {
        while (true) { delay(5_000); tick++ }
    }
    val nodes = remember(tick) { NoraRouteSelector.load() }
    val ranked = remember(nodes) { NoraRouteSelector.rank(nodes) }
    val locations = remember(ranked) { NoraRouteSelector.locations(ranked) }
    val candidate = if (auto) {
        ranked.firstOrNull { it.latencyMs > 0L } ?: ranked.firstOrNull()
    } else {
        locations.firstOrNull { it.countryCode == chosenCountry } ?: ranked.firstOrNull()
    }

    MaterialTheme(colorScheme = darkColorScheme(primary=amber, onPrimary=bg, background=bg, surface=surface)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Scaffold(containerColor=bg) { padding ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                        .background(Brush.verticalGradient(listOf(bg, Color(0xFF172D32), bg)))
                        .padding(padding).padding(horizontal=18.dp),
                    verticalArrangement=Arrangement.spacedBy(16.dp),
                    contentPadding=androidx.compose.foundation.layout.PaddingValues(top=22.dp,bottom=30.dp)
                ) {
                    item {
                        Row(verticalAlignment=Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(13.dp)) {
                            Box(modifier=Modifier.size(50.dp).background(amber,RoundedCornerShape(16.dp)),
                                contentAlignment=Alignment.Center) {
                                Text("N",fontSize=29.sp,fontWeight=FontWeight.Black,color=bg)
                            }
                            Column {
                                Text("NoraProxy",color=white,fontSize=25.sp,fontWeight=FontWeight.ExtraBold)
                                Text("اتصال هوشمند و مستقل",color=muted,fontSize=12.sp)
                            }
                        }
                    }
                    item {
                        Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF26493F)),
                            shape=RoundedCornerShape(25.dp)) {
                            Column(modifier=Modifier.fillMaxWidth().padding(20.dp),
                                verticalArrangement=Arrangement.spacedBy(14.dp)) {
                                Text(if (state.isRunning) "VPN متصل است" else "آماده اتصال",
                                    color=white,fontSize=23.sp,fontWeight=FontWeight.ExtraBold)
                                Text("اتصال مستقیم با موتور Xray داخل NoraProxy",
                                    fontSize=13.sp,color=Color(0xFFD4E5DE))
                                if (candidate != null) {
                                    Text(candidate.flag + " " + candidate.countryName +
                                        if (candidate.latencyMs > 0) " • " + candidate.latencyMs + "ms"
                                        else " • پینگ نامشخص",color=mint)
                                }
                                Button(
                                    modifier=Modifier.fillMaxWidth().height(55.dp),
                                    enabled=state.isRunning || candidate != null,
                                    shape=RoundedCornerShape(17.dp),
                                    colors=ButtonDefaults.buttonColors(
                                        containerColor=if (state.isRunning) Color(0xFFFFB3A4) else mint,
                                        contentColor=bg),
                                    onClick={
                                        if (state.isRunning) onAction(MainAction.ToggleService)
                                        else candidate?.let { node ->
                                            if (state.selectedGuid != node.guid) {
                                                onAction(MainAction.SelectServer(node.guid))
                                            }
                                            // MainActivity requests VPN permission and starts the
                                            // original v2rayNG CoreVpnService in this same APK.
                                            onAction(MainAction.ToggleService)
                                        }
                                    }
                                ) {
                                    Text(if (state.isRunning) "قطع اتصال" else "⚡ اتصال به NoraProxy",
                                        fontSize=17.sp,fontWeight=FontWeight.Bold)
                                }
                            }
                        }
                    }
                    item {
                        Text("اشتراک من",fontSize=19.sp,fontWeight=FontWeight.Bold,color=white)
                        Spacer(Modifier.height(10.dp))
                        Card(colors=CardDefaults.cardColors(containerColor=surface),
                            shape=RoundedCornerShape(22.dp)) {
                            Column(modifier=Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value=subscription,onValueChange={subscription=it.take(4096)},
                                    label={Text("لینک اشتراک یا کانفیگ")},
                                    modifier=Modifier.fillMaxWidth(),maxLines=3,
                                    shape=RoundedCornerShape(16.dp))
                                Button(
                                    enabled=subscription.isNotBlank() && !loading,
                                    modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(15.dp),
                                    onClick={
                                        onAction(MainAction.ImportBatchConfig(subscription.trim()))
                                        subscription=""
                                    }
                                ) { Text("افزودن اشتراک") }
                                OutlinedButton(
                                    onClick={onAction(MainAction.UpdateSubscriptions)},
                                    modifier=Modifier.fillMaxWidth(),enabled=!loading
                                ) { Text("همگام‌سازی آخرین سرورها") }
                                if(loading) {
                                    Row(verticalAlignment=Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier=Modifier.size(18.dp),strokeWidth=2.dp)
                                        Spacer(Modifier.size(10.dp))
                                        Text("در حال دریافت...",color=muted)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Text("انتخاب لوکیشن",fontSize=19.sp,fontWeight=FontWeight.Bold,color=white)
                        Text("از هر کشور فقط بهترین مسیر نمایش داده می‌شود.",
                            color=muted,fontSize=12.sp)
                    }
                    item {
                        LocationRow("✨","انتخاب خودکار",null,auto) {
                            auto=true
                            chosenCountry=null
                        }
                    }
                    items(locations,key={it.countryCode}) { location ->
                        LocationRow(location.flag,location.countryName,
                            location.latencyMs.takeIf { it>0 },
                            !auto && chosenCountry==location.countryCode) {
                            auto=false
                            chosenCountry=location.countryCode
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick={onAction(MainAction.TestRealAllServers)},
                            modifier=Modifier.fillMaxWidth(),
                            enabled=nodes.isNotEmpty() && !state.isTesting
                        ) {
                            Text(if (state.isTesting) "در حال تست پینگ واقعی..."
                                else "تست پینگ واقعی سرورها")
                        }
                    }
                    item {
                        Text("نخستین اتصال نیازمند تأیید مجوز VPN اندروید است. " +
                            "نتایج پینگ قبلی پس از تغییر شبکه ممکن است معتبر نباشند.",
                            color=muted,fontSize=11.sp,lineHeight=19.sp,
                            modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationRow(
    flag:String,title:String,ping:Long?,selected:Boolean,onClick:()->Unit
) {
    Row(
        modifier=Modifier.fillMaxWidth()
            .background(if(selected) Color(0xFF2A453C) else surface,RoundedCornerShape(17.dp))
            .then(if(selected) Modifier.border(1.dp,mint,RoundedCornerShape(17.dp)) else Modifier)
            .clickable(onClick=onClick).padding(16.dp),
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(13.dp)
    ) {
        Text(flag,fontSize=26.sp)
        Text(title,modifier=Modifier.weight(1f),color=white,
            fontSize=15.sp,fontWeight=FontWeight.Bold)
        Text(if(selected) "✓" else ping?.let{it.toString()+" ms"} ?: "—",
            color=mint,fontSize=13.sp)
    }
}
