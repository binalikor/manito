package com.stocksecretary.app

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Locale

private val PriceRed = Color(0xFFE1261C)
private val BrandBlue = Color(0xFF1769AA)
private val FavoriteBlue = Color(0xFFD9ECFA)
private val WarningBackground = Color(0xFFFFE5E5)
private val ApiBaseUrl = BuildConfig.API_BASE_URL.trimEnd('/')

private data class StockQuote(
    val name: String,
    val price: String,
    val change: String,
    val changeRate: String,
    val previousClose: String,
    val open: String,
    val high: String,
    val low: String,
    val volume: String,
    val tradingValue: String,
    val marketCap: String,
    val foreignExhaustionRate: String,
    val high52Week: String,
    val low52Week: String,
    val per: String,
    val eps: String,
    val pbr: String,
    val bps: String,
    val warning: Boolean
)

private data class NewsItem(val title: String, val date: String, val url: String)
private data class DetailListItem(val title: String, val date: String, val url: String = "")
private data class RankedStock(
    val code: String,
    val name: String,
    val price: String,
    val changeRate: String,
    val volume: String,
    val marketCap: String
)
private data class InvestorRow(
    val date: String,
    val foreignNetBuy: String,
    val foreignHoldingRate: String,
    val institutionNetBuy: String,
    val personalNetBuy: String,
    val close: String,
    val change: String,
    val volume: String
)

private suspend fun fetchStockQuote(code: String): StockQuote = withContext(Dispatchers.IO) {
    val connection = URL("$ApiBaseUrl/api/stocks/$code/quote").openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.requestMethod = "GET"
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException(JSONObject(body).optString("message", "시세 조회에 실패했습니다."))
        }
        val json = JSONObject(body)
        StockQuote(
            name = json.optString("name", "삼성전자"),
            price = json.optString("price"),
            change = json.optString("change"),
            changeRate = json.optString("changeRate"),
            previousClose = json.optString("previousClose"),
            open = json.optString("open"),
            high = json.optString("high"),
            low = json.optString("low"),
            volume = json.optString("volume"),
            tradingValue = json.optString("tradingValue"),
            marketCap = json.optString("marketCap"),
            foreignExhaustionRate = json.optString("foreignExhaustionRate"),
            high52Week = json.optString("high52Week"),
            low52Week = json.optString("low52Week"),
            per = json.optString("per"),
            eps = json.optString("eps"),
            pbr = json.optString("pbr"),
            bps = json.optString("bps"),
            warning = json.optBoolean("warning")
        )
    } finally {
        connection.disconnect()
    }
}

private suspend fun fetchStockNews(code: String, name: String): List<NewsItem> = withContext(Dispatchers.IO) {
    val encodedName = java.net.URLEncoder.encode(name, Charsets.UTF_8.name())
    val connection = URL("$ApiBaseUrl/api/stocks/$code/news?name=$encodedName").openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException(JSONObject(body).optString("message", "뉴스 조회에 실패했습니다."))
        }
        val items = JSONObject(body).getJSONArray("items")
        buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                add(NewsItem(item.optString("title"), item.optString("date"), item.optString("url")))
            }
        }
    } finally {
        connection.disconnect()
    }
}

private suspend fun fetchInvestorRows(code: String): List<InvestorRow> = withContext(Dispatchers.IO) {
    val connection = URL("$ApiBaseUrl/api/stocks/$code/investors").openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException(JSONObject(body).optString("message", "투자자 동향 조회에 실패했습니다."))
        }
        val rows = JSONObject(body).getJSONArray("rows")
        buildList {
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                add(
                    InvestorRow(
                        date = row.optString("date"),
                        foreignNetBuy = row.optString("foreignNetBuy"),
                        foreignHoldingRate = row.optString("foreignHoldingRate"),
                        institutionNetBuy = row.optString("institutionNetBuy"),
                        personalNetBuy = row.optString("personalNetBuy"),
                        close = row.optString("close"),
                        change = row.optString("change"),
                        volume = row.optString("volume")
                    )
                )
            }
        }
    } finally {
        connection.disconnect()
    }
}

private suspend fun fetchDisclosureItems(code: String, irOnly: Boolean): List<NewsItem> = withContext(Dispatchers.IO) {
    val suffix = if (irOnly) "?type=ir" else ""
    val connection = URL("$ApiBaseUrl/api/stocks/$code/disclosures$suffix").openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 12_000
        connection.readTimeout = 12_000
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException(JSONObject(body).optString("message", "공시 조회에 실패했습니다."))
        }
        val items = JSONObject(body).getJSONArray("items")
        buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                add(NewsItem(item.optString("title"), item.optString("date"), item.optString("url")))
            }
        }
    } finally {
        connection.disconnect()
    }
}

private suspend fun fetchHomeNews(): List<NewsItem> = withContext(Dispatchers.IO) {
    val connection = URL("$ApiBaseUrl/api/home/news").openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) throw IllegalStateException(JSONObject(body).optString("message", "뉴스 조회 실패"))
        val items = JSONObject(body).getJSONArray("items")
        buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                add(NewsItem(item.optString("title"), item.optString("date"), item.optString("url")))
            }
        }
    } finally {
        connection.disconnect()
    }
}

private suspend fun fetchRankings(type: String): List<RankedStock> = withContext(Dispatchers.IO) {
    val connection = URL("$ApiBaseUrl/api/home/rankings/$type").openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) throw IllegalStateException(JSONObject(body).optString("message", "시장 순위 조회 실패"))
        val items = JSONObject(body).getJSONArray("items")
        buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                add(
                    RankedStock(
                        code = item.optString("code"), name = item.optString("name"),
                        price = item.optString("price"), changeRate = item.optString("changeRate"),
                        volume = item.optString("volume"), marketCap = item.optString("marketCap")
                    )
                )
            }
        }
    } finally {
        connection.disconnect()
    }
}

private fun formattedNumber(value: String, suffix: String = ""): String {
    val number = value.toLongOrNull() ?: return if (value.isBlank()) "-" else value + suffix
    return NumberFormat.getNumberInstance(Locale.KOREA).format(number) + suffix
}

private fun formattedSignedNumber(value: String): String {
    val number = value.toLongOrNull() ?: return if (value.isBlank()) "-" else value
    val sign = if (number > 0) "+" else ""
    return sign + NumberFormat.getNumberInstance(Locale.KOREA).format(number)
}

private fun formattedDate(value: String): String =
    if (value.length == 8) "${value.substring(4, 6)}.${value.substring(6, 8)}" else value

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var selectedStockCode by remember { mutableStateOf<String?>(null) }
                if (selectedStockCode != null) {
                    StockDetailScreen(
                        stockCode = selectedStockCode!!,
                        onBack = { selectedStockCode = null }
                    )
                } else {
                    IntroScreen(onStockClick = { selectedStockCode = it })
                }
            }
        }
    }
}

@Composable
fun IntroScreen(
    onStockClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMarket by remember { mutableIntStateOf(0) }
    var rankingTab by remember { mutableIntStateOf(0) }
    var homeNews by remember { mutableStateOf<List<NewsItem>>(emptyList()) }
    var homeNewsError by remember { mutableStateOf<String?>(null) }
    var rankings by remember { mutableStateOf<List<RankedStock>>(emptyList()) }
    var rankingError by remember { mutableStateOf<String?>(null) }
    var watchQuotes by remember { mutableStateOf<Map<String, StockQuote>>(emptyMap()) }
    val marketNames = listOf("코스피", "코스닥", "코스피200")
    val marketValues = listOf("2,620.02", "814.29", "348.67")
    val marketChanges = listOf("▲ 0.47%", "▼ 0.18%", "▲ 0.35%")
    val investorFlows = listOf(
        listOf("+1,024억", "-842억", "-206억"),
        listOf("-318억", "+124억", "+231억"),
        listOf("+526억", "-219억", "-344억")
    )
    val rankingTabs = listOf("거래량 상위", "상승률", "하락률", "시가총액")
    val rankingTypes = listOf("volume", "rising", "falling", "marketCap")
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        runCatching { fetchHomeNews() }
            .onSuccess { homeNews = it; homeNewsError = null }
            .onFailure { homeNewsError = it.message ?: "뉴스를 불러오지 못했습니다." }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val updated = mutableMapOf<String, StockQuote>()
            listOf("005930", "000660").forEach { code ->
                runCatching { fetchStockQuote(code) }.onSuccess { updated[code] = it }
            }
            if (updated.isNotEmpty()) watchQuotes = updated
            delay(30_000)
        }
    }
    LaunchedEffect(rankingTab) {
        rankings = emptyList()
        rankingError = null
        runCatching { fetchRankings(rankingTypes[rankingTab]) }
            .onSuccess { rankings = it }
            .onFailure { rankingError = it.message ?: "시장 순위를 불러오지 못했습니다." }
    }

    Surface(modifier = modifier.fillMaxSize(), color = Color(0xFFF6F7F9)) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 30.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Text("주식비서", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    marketNames.forEachIndexed { index, name ->
                        DetailTab(
                            title = name,
                            selected = selectedMarket == index,
                            onClick = { selectedMarket = index }
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text(
                    marketValues[selectedMarket],
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    marketChanges[selectedMarket],
                    fontSize = 13.sp,
                    color = if (marketChanges[selectedMarket].startsWith("▲")) PriceRed else BrandBlue
                )

                Spacer(Modifier.height(14.dp))
                Text("오늘의 투자자 동향", fontSize = 11.sp, color = Color(0xFF686D73))
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FlowValue("개인", investorFlows[selectedMarket][0])
                    FlowValue("기관", investorFlows[selectedMarket][1])
                    FlowValue("외국인", investorFlows[selectedMarket][2])
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${marketNames[selectedMarket]} 1분 선차트", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("09:00–15:30", fontSize = 11.sp, color = Color.Gray)
                }
                Spacer(Modifier.height(7.dp))
                MarketLineChart(
                    marketIndex = selectedMarket,
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                )
            }

            HomeSection(title = "내 종목 · 최근 조회") {
                Column {
                    StockListHeader()
                    val samsung = watchQuotes["005930"]
                    StockListRow(
                        samsung?.name ?: "삼성전자",
                        samsung?.let { formattedNumber(it.price, "원") } ?: "불러오는 중",
                        samsung?.let { if (it.changeRate.startsWith("-")) "${it.changeRate}%" else "+${it.changeRate}%" } ?: "",
                        { onStockClick("005930") }
                    )
                    HorizontalDivider(color = Color(0xFFEEF0F2))
                    val hynix = watchQuotes["000660"]
                    StockListRow(
                        hynix?.name ?: "SK하이닉스",
                        hynix?.let { formattedNumber(it.price, "원") } ?: "불러오는 중",
                        hynix?.let { if (it.changeRate.startsWith("-")) "${it.changeRate}%" else "+${it.changeRate}%" } ?: "",
                        { onStockClick("000660") }
                    )
                }
            }

            HomeSection(title = "주요 뉴스") {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    if (homeNews.isEmpty()) {
                        Text(homeNewsError ?: "뉴스를 불러오는 중입니다.", modifier = Modifier.padding(vertical = 24.dp), color = Color.Gray, fontSize = 13.sp)
                    } else {
                        homeNews.forEachIndexed { index, item ->
                            NewsRow(item.title, item.date) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                            }
                            if (index < homeNews.lastIndex) HorizontalDivider(color = Color(0xFFEEF0F2))
                        }
                    }
                }
            }

            HomeSection(title = "시장 순위") {
                Column {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp)
                    ) {
                        rankingTabs.forEachIndexed { index, title ->
                            DetailTab(
                                title = title,
                                selected = rankingTab == index,
                                onClick = { rankingTab = index }
                            )
                        }
                    }
                    if (rankings.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(130.dp), contentAlignment = Alignment.Center) {
                            Text(rankingError ?: "${rankingTabs[rankingTab]} 불러오는 중", color = Color(0xFF777C82), fontSize = 14.sp)
                        }
                    } else {
                        StockListHeader()
                        rankings.forEachIndexed { index, stock ->
                            StockListRow(
                                stock.name,
                                formattedNumber(stock.price, "원"),
                                if (stock.changeRate.startsWith("-")) "${stock.changeRate}%" else "+${stock.changeRate}%",
                                { onStockClick(stock.code) }
                            )
                            if (index < rankings.lastIndex) HorizontalDivider(color = Color(0xFFEEF0F2))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowValue(name: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, fontSize = 12.sp, color = Color(0xFF6C7075))
        Spacer(Modifier.width(5.dp))
        Text(
            value,
            fontSize = 12.sp,
            color = if (value.startsWith("+")) PriceRed else BrandBlue,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun HomeSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)) {
        Text(
            title,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 9.dp),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 1.dp,
            content = content
        )
    }
}

@Composable
private fun StockListHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF7F8FA))
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text("종목", modifier = Modifier.weight(1f), fontSize = 12.sp, color = Color.Gray)
        Text("현재가", modifier = Modifier.width(92.dp), fontSize = 12.sp, color = Color.Gray)
        Text("등락률", modifier = Modifier.width(62.dp), fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
private fun StockListRow(name: String, price: String, change: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, modifier = Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(price, modifier = Modifier.width(92.dp), fontSize = 14.sp)
        Text(
            change,
            modifier = Modifier.width(62.dp),
            fontSize = 13.sp,
            color = if (change.startsWith("+")) PriceRed else BrandBlue
        )
    }
}

@Composable
private fun NewsRow(title: String, time: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f), fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.width(10.dp))
        Text(time, fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable
private fun MarketLineChart(marketIndex: Int, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .border(1.dp, Color(0xFFE7E9EC), RoundedCornerShape(4.dp))
            .padding(10.dp)
    ) {
        repeat(3) { index ->
            val y = size.height * index / 2f
            drawLine(Color(0xFFF0F1F3), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        val chartValues = when (marketIndex) {
            1 -> listOf(0.35f, 0.28f, 0.46f, 0.38f, 0.54f, 0.49f, 0.62f, 0.56f, 0.68f)
            2 -> listOf(0.64f, 0.52f, 0.57f, 0.43f, 0.47f, 0.32f, 0.39f, 0.24f, 0.29f)
            else -> listOf(0.58f, 0.46f, 0.63f, 0.40f, 0.48f, 0.30f, 0.38f, 0.22f, 0.28f)
        }
        val path = Path().apply {
            chartValues.forEachIndexed { index, value ->
                val x = size.width * index / (chartValues.size - 1)
                val y = size.height * value
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        val lineColor = if (marketChangesDown(marketIndex)) BrandBlue else PriceRed
        drawPath(path, lineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

private fun marketChangesDown(marketIndex: Int): Boolean = marketIndex == 1

@Composable
fun StockDetailScreen(
    modifier: Modifier = Modifier,
    stockCode: String = "005930",
    onBack: (() -> Unit)? = null
) {
    var isFavorite by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var quote by remember { mutableStateOf<StockQuote?>(null) }
    var quoteError by remember { mutableStateOf<String?>(null) }
    var newsItems by remember { mutableStateOf<List<NewsItem>>(emptyList()) }
    var newsError by remember { mutableStateOf<String?>(null) }
    var investorRows by remember { mutableStateOf<List<InvestorRow>>(emptyList()) }
    var disclosureItems by remember { mutableStateOf<List<NewsItem>>(emptyList()) }
    var irItems by remember { mutableStateOf<List<NewsItem>>(emptyList()) }
    var disclosureError by remember { mutableStateOf<String?>(null) }
    val tabs = listOf("기본정보", "뉴스 공시", "분석", "리서치")
    val hasRiskDisclosure = disclosureItems.any { item ->
        listOf("관리종목", "거래정지", "매매거래정지", "상장폐지", "불성실공시").any { keyword ->
            item.title.contains(keyword)
        }
    }

    LaunchedEffect(stockCode) {
        while (true) {
            runCatching { fetchStockQuote(stockCode) }
                .onSuccess {
                    quote = it
                    quoteError = null
                }
                .onFailure { quoteError = it.message ?: "시세 조회에 실패했습니다." }
            delay(15_000)
        }
    }
    LaunchedEffect(stockCode, quote?.name) {
        val name = quote?.name ?: "삼성전자"
        runCatching { fetchStockNews(stockCode, name) }
            .onSuccess {
                newsItems = it
                newsError = null
            }
            .onFailure { newsError = it.message ?: "뉴스 조회에 실패했습니다." }
    }
    LaunchedEffect(stockCode) {
        runCatching { fetchInvestorRows(stockCode) }
            .onSuccess { investorRows = it }
    }
    LaunchedEffect(stockCode) {
        runCatching {
            fetchDisclosureItems(stockCode, false) to fetchDisclosureItems(stockCode, true)
        }.onSuccess {
            disclosureItems = it.first
            irItems = it.second
            disclosureError = null
        }.onFailure { disclosureError = it.message ?: "공시 조회에 실패했습니다." }
    }

    Surface(modifier = modifier.fillMaxSize(), color = Color.White) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(top = 22.dp, bottom = 32.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                if (onBack != null) {
                    Text(
                        "← 홈",
                        modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp),
                        color = BrandBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("A$stockCode", color = Color(0xFF5F6368), fontSize = 13.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("코스피", color = Color(0xFF5F6368), fontSize = 13.sp)
                }

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(quote?.name ?: "삼성전자", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text("보유", color = Color(0xFF4CAF50), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            quote?.let { formattedNumber(it.price, "원") } ?: "현재가 불러오는 중",
                            color = PriceRed,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            quote?.let { "${formattedNumber(it.change)}  (${it.changeRate}%)" } ?: (quoteError ?: ""),
                            color = PriceRed,
                            fontSize = 15.sp
                        )
                    }
                    MiniPriceChart(modifier = Modifier.size(width = 145.dp, height = 86.dp))
                }

                Spacer(Modifier.height(24.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isFavorite = !isFavorite },
                    color = if (isFavorite) BrandBlue else FavoriteBlue,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isFavorite) "관심종목 등록됨" else "관심종목 등록",
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = if (isFavorite) Color.White else Color(0xFF124C73),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                Spacer(Modifier.height(22.dp))

                if (hasRiskDisclosure) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = WarningBackground,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
                            Text("주의", color = PriceRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "관리종목으로 거래정지 또는 상장폐지 가능성이 있어요.",
                                color = Color(0xFF6B2222),
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    DetailTab(
                        modifier = Modifier.weight(1f),
                        title = title,
                        selected = selectedTab == index,
                        onClick = { selectedTab = index }
                    )
                }
            }

            when (selectedTab) {
                0 -> StockSummaryContent(
                    quote = quote,
                    investorRows = investorRows,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
                )
                1 -> NewsDisclosureContent(
                    initialTab = 0,
                    showTabs = true,
                    liveNews = newsItems,
                    newsError = newsError,
                    liveDisclosures = disclosureItems,
                    liveIr = irItems,
                    disclosureError = disclosureError,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
                3 -> ResearchContent(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
                else -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 28.dp)
                        .height(170.dp)
                        .background(Color(0xFFF7F8FA), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${tabs[selectedTab]} 내용이 이곳에 표시됩니다.",
                        color = Color(0xFF7A7F86),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ResearchContent(modifier: Modifier = Modifier) {
    val reports: List<Triple<String, String, String>> = emptyList()
    val formatter = remember { java.text.SimpleDateFormat("yyyy.MM.dd", java.util.Locale.KOREA) }
    val oneYearAgo = remember {
        java.util.Calendar.getInstance().apply { add(java.util.Calendar.YEAR, -1) }.time
    }
    val recentReports = remember(reports) {
        reports.filter { report ->
            formatter.parse(report.third)?.let { !it.before(oneYearAgo) } ?: false
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (recentReports.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(150.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("리포트가 없습니다", color = Color.Gray, fontSize = 14.sp)
            }
        } else {
            recentReports.forEachIndexed { index, report ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(report.first, fontSize = 13.sp, lineHeight = 19.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(report.second, color = Color.Gray, fontSize = 11.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(report.third, color = Color.Gray, fontSize = 11.sp)
                }
                if (index < recentReports.lastIndex) {
                    HorizontalDivider(color = Color(0xFFEEF0F2))
                }
            }
        }
    }
}

@Composable
private fun NewsDisclosureContent(
    modifier: Modifier = Modifier,
    initialTab: Int = 0,
    showTabs: Boolean = true,
    liveNews: List<NewsItem> = emptyList(),
    newsError: String? = null,
    liveDisclosures: List<NewsItem> = emptyList(),
    liveIr: List<NewsItem> = emptyList(),
    disclosureError: String? = null
) {
    var selectedSubTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    val subTabs = listOf("종목뉴스", "공시정보", "IR정보")
    val context = LocalContext.current
    val items = listOf(
        liveNews.map { DetailListItem(it.title, it.date, it.url) },
        liveDisclosures.map { DetailListItem(it.title, it.date, it.url) },
        liveIr.map { DetailListItem(it.title, it.date, it.url) }
    )

    Column(modifier = modifier.fillMaxWidth()) {
        if (showTabs) {
            Row(modifier = Modifier.fillMaxWidth()) {
                subTabs.forEachIndexed { index, title ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedSubTab = index },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            title,
                            modifier = Modifier.padding(vertical = 11.dp),
                            color = if (selectedSubTab == index) BrandBlue else Color(0xFF555555),
                            fontSize = 14.sp,
                            fontWeight = if (selectedSubTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(if (selectedSubTab == index) 2.dp else 1.dp)
                                .background(if (selectedSubTab == index) BrandBlue else Color(0xFFE3E5E8))
                        )
                    }
                }
            }
        }
        Column {
            if (items[selectedSubTab].isEmpty()) {
                Text(
                    when (selectedSubTab) {
                        0 -> newsError ?: "뉴스를 불러오는 중입니다."
                        1 -> disclosureError ?: "공시가 없습니다"
                        else -> disclosureError ?: "IR정보가 없습니다"
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
            items[selectedSubTab].forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (item.url.isNotBlank()) Modifier.clickable {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                            } else Modifier
                        )
                        .padding(vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item.title, modifier = Modifier.weight(1f), fontSize = 13.sp, lineHeight = 19.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(item.date, color = Color.Gray, fontSize = 11.sp)
                }
                if (index < items[selectedSubTab].lastIndex) {
                    HorizontalDivider(color = Color(0xFFEEF0F2))
                }
            }
        }
    }
}

@Composable
private fun StockSummaryContent(
    modifier: Modifier = Modifier,
    quote: StockQuote? = null,
    investorRows: List<InvestorRow> = emptyList()
) {
    val metrics = listOf(
        listOf("전일", quote?.let { formattedNumber(it.previousClose, "원") } ?: "-", "시가", quote?.let { formattedNumber(it.open, "원") } ?: "-"),
        listOf("고가", quote?.let { formattedNumber(it.high, "원") } ?: "-", "저가", quote?.let { formattedNumber(it.low, "원") } ?: "-"),
        listOf("거래량", quote?.let { formattedNumber(it.volume, "주") } ?: "-", "거래대금", quote?.let { formattedNumber(it.tradingValue, "원") } ?: "-"),
        listOf("시가총액", quote?.let { formattedNumber(it.marketCap, "억원") } ?: "-", "외인소진율", quote?.foreignExhaustionRate?.let { "$it%" } ?: "-"),
        listOf("52주 최고", quote?.let { formattedNumber(it.high52Week, "원") } ?: "-", "52주 최저", quote?.let { formattedNumber(it.low52Week, "원") } ?: "-"),
        listOf("PER", quote?.per?.let { "${it}배" } ?: "-", "EPS", quote?.let { formattedNumber(it.eps, "원") } ?: "-"),
        listOf("추정 PER", "16.8배", "추정 EPS", "15,446원"),
        listOf("PBR", quote?.pbr?.let { "${it}배" } ?: "-", "BPS", quote?.let { formattedNumber(it.bps, "원") } ?: "-"),
        listOf("배당수익률", "2.18%", "주당배당금", "5,650원")
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Text("주요 지표", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.border(1.dp, Color(0xFFE2E5E9), RoundedCornerShape(6.dp))) {
            metrics.forEachIndexed { index, row ->
                SummaryMetricRow(row)
                if (index < metrics.lastIndex) HorizontalDivider(color = Color(0xFFEEF0F2))
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("투자자별 매매동향", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Column(
                modifier = Modifier
                    .width(550.dp)
                    .border(1.dp, Color(0xFFE2E5E9), RoundedCornerShape(6.dp))
            ) {
                InvestorHistoryRow(
                    listOf("일자", "외국인(보유율)", "기관", "개인", "종가", "전일비", "거래량"),
                    header = true
                )
                HorizontalDivider(color = Color(0xFFEEF0F2))
                if (investorRows.isEmpty()) {
                    InvestorHistoryRow(listOf("-", "불러오는 중", "-", "-", "-", "-", "-"))
                } else {
                    investorRows.forEachIndexed { index, row ->
                        val foreign = buildString {
                            append(formattedSignedNumber(row.foreignNetBuy))
                            if (row.foreignHoldingRate.isNotBlank()) append("(${row.foreignHoldingRate}%)")
                        }
                        InvestorHistoryRow(
                            listOf(
                                formattedDate(row.date), foreign,
                                formattedSignedNumber(row.institutionNetBuy),
                                formattedSignedNumber(row.personalNetBuy),
                                formattedNumber(row.close), formattedSignedNumber(row.change),
                                formattedNumber(row.volume)
                            )
                        )
                        if (index < investorRows.lastIndex) HorizontalDivider(color = Color(0xFFEEF0F2))
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricRow(values: List<String>) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp)) {
        Text(values[0], modifier = Modifier.weight(0.8f), fontSize = 11.sp, color = Color.Gray)
        Text(values[1], modifier = Modifier.weight(1.2f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(values[2], modifier = Modifier.weight(0.9f), fontSize = 11.sp, color = Color.Gray)
        Text(values[3], modifier = Modifier.weight(1.1f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun InvestorHistoryRow(
    values: List<String>,
    header: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (header) Color(0xFFF7F8FA) else Color.Transparent)
            .padding(horizontal = 6.dp, vertical = 8.dp)
    ) {
        val widths = listOf(38.dp, 108.dp, 75.dp, 75.dp, 75.dp, 65.dp, 95.dp)
        values.forEachIndexed { index, value ->
            Text(
                value,
                modifier = Modifier.width(widths[index]),
                fontSize = 11.sp,
                color = when {
                    header -> Color.Gray
                    value.startsWith("+") -> PriceRed
                    value.startsWith("-") -> BrandBlue
                    else -> Color(0xFF333333)
                },
                fontWeight = if (header) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun DetailTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            modifier = Modifier.padding(vertical = 12.dp),
            color = if (selected) BrandBlue else Color(0xFF333333),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (selected) 3.dp else 1.dp)
                .background(if (selected) BrandBlue else Color(0xFFE3E5E8))
        )
    }
}

@Composable
private fun MiniPriceChart(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text("3분봉", modifier = Modifier.align(Alignment.End), color = Color.Gray, fontSize = 11.sp)
        Canvas(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            val chartPath = Path().apply {
                moveTo(0f, size.height * 0.72f)
                lineTo(size.width * 0.13f, size.height * 0.60f)
                lineTo(size.width * 0.25f, size.height * 0.67f)
                lineTo(size.width * 0.39f, size.height * 0.43f)
                lineTo(size.width * 0.52f, size.height * 0.50f)
                lineTo(size.width * 0.67f, size.height * 0.28f)
                lineTo(size.width * 0.80f, size.height * 0.36f)
                lineTo(size.width, size.height * 0.12f)
            }
            drawLine(Color(0xFFE8EAED), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            drawPath(chartPath, PriceRed, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 800)
@Composable
private fun StockDetailPreview() {
    MaterialTheme { StockDetailScreen() }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun IntroScreenPreview() {
    MaterialTheme { IntroScreen(onStockClick = {}) }
}
