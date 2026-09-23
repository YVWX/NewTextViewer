package com.text.newtextviewer

import android.icu.util.Calendar
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.text.isDigitsOnly
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel
import com.text.newtextviewer.ui.theme.NewTextViewerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.Date
import kotlin.math.min

const val CHUNK_SIZE = 65536

const val KEYWORD_LENGTH = 256

const val WRAP_LENGTH = 256

val CHARSETS = arrayOf(
    Charsets.ISO_8859_1,
    Charsets.US_ASCII,
    Charsets.UTF_16,
    Charsets.UTF_16BE,
    Charsets.UTF_16LE,
    Charsets.UTF_32,
    Charsets.UTF_32BE,
    Charsets.UTF_32LE,
    Charsets.UTF_8
)

const val HELP_INFO = "The lines too long will be wrapped automatically and they cannot be unwrapped. The edit mode saves files automatically as ntv_XXX.YYY to avoid the file loss."


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NewTextViewerTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainNavHost()
                }
            }
        }
    }
}

fun countOccurrences(text: String, target: String): Int {
    var count = 0
    var index = text.indexOf(target)

    while (index != -1) {
        count++
        index = text.indexOf(target, index + 1)
    }

    return count
}

@Composable
fun LineLeadingIcon(index: Int, expandEnable: Boolean, viewModel: TextViewerViewModel) {
    val lineNumber = viewModel.baseLineNumber + index + 1
    val textSize = with(LocalDensity.current) { 24.sp.toDp() }
    Row {
        Box {
            Text(
                text = lineNumber.toString(),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .height(textSize)
                    .defaultMinSize(textSize, textSize)
                    .wrapContentHeight(),
                style = TextStyle(fontSize = 16.sp)
            )
        }
        IconButton(
            onClick = {
                viewModel.expandedList[index] = !viewModel.expandedList[index]
            },
            modifier = Modifier.height(textSize),
            enabled = expandEnable
        ) {
            if (viewModel.expandedList[index]) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    null
                )
            }
            else {
                Icon(
                    Icons.Filled.KeyboardArrowRight,
                    null
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Main(modifier: Modifier = Modifier, viewModel: TextViewerViewModel = viewModel()) {
    val configuration = LocalConfiguration.current

    val context = LocalContext.current

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var inputKeyword by remember { mutableStateOf("") }
    var inputLine by remember { mutableStateOf("0") }

    var newFileFlag by remember { mutableStateOf(false) }

    var readTextFlag by remember { mutableStateOf(false) }

    var writeFlag by remember { mutableStateOf(false) }

    var scrollFlag by remember { mutableStateOf(false) }

    var syncEditFlag by remember { mutableStateOf(false) }

    val textSizeDp = with(LocalDensity.current) { 24.sp.toDp() }
    val textSizePx = with(LocalDensity.current) { 24.sp.roundToPx() }

    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        if (it != null) {
            viewModel.treeUri = it
            viewModel.treeSelectedFlag = true
        }
    }

    val newLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) {
            val cursor = context.contentResolver.query(it, null, null, null, null)
            var fileName = ""
            var fileSize = 0
            if (cursor != null) {
                cursor.moveToFirst()
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                fileName = cursor.getString(nameIndex)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                fileSize = cursor.getInt(sizeIndex)
                cursor.close()
            }

            viewModel.fileReadInfo.add(
                FileReadInfo(
                    it,
                    fileName,
                    fileSize,
                    Charsets.UTF_8
                )
            )
            viewModel.currentChunkInfo.add(0)
            viewModel.chunkNumberInfo.add(0)
            newFileFlag = true
        }
    }

    if (newFileFlag) {
        viewModel.lazyListStateInfo.add(rememberLazyListState())
        viewModel.currentFileIndex = viewModel.fileReadInfo.size - 1
        newFileFlag = false
        readTextFlag = true
    }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(writeFlag, viewModel.treeSelectedFlag) {
        if (writeFlag && viewModel.treeSelectedFlag) {
            writeFlag = false

            if (viewModel.fileReadInfo[viewModel.currentFileIndex].fileSize > 1000 * 1000) {
                val sizeMB = viewModel.fileReadInfo[viewModel.currentFileIndex].fileSize.toDouble() / 1000 / 1000
                val estimatedTime = sizeMB / 1000 * 160
                val loadingInfo =
                    "File size: " + "%.2f".format(sizeMB) + "MB. Take approx " + "%.2f".format(
                        estimatedTime
                    ) + "s."
                viewModel.loadingText = loadingInfo
                val toast = Toast.makeText(context, loadingInfo, Toast.LENGTH_LONG)
                toast.show()
                viewModel.loadingFlag = true
            }

            withContext(Dispatchers.IO) {
                val treeDocumentFile = viewModel.treeUri?.let { DocumentFile.fromTreeUri(context, it) }

                val inputStream = context.contentResolver.openInputStream(viewModel.fileReadInfo[viewModel.currentFileIndex].uri)
                val tempFile = File(context.filesDir, "temp.txt")

                if (inputStream != null) {
                    val bufferedReader = inputStream.bufferedReader(charset = viewModel.fileReadInfo[viewModel.currentFileIndex].charset)
                    val bufferedWriter = tempFile.bufferedWriter(charset = viewModel.fileReadInfo[viewModel.currentFileIndex].charset)

                    var chunkCount = 0
                    val skipSize = CHUNK_SIZE / 2

                    while (bufferedReader.ready()) {
                        val buffer = CharArray(CHUNK_SIZE)
                        val actualLength = bufferedReader.read(buffer, 0, skipSize)

                        if (chunkCount < viewModel.currentChunkInfo[viewModel.currentFileIndex] || chunkCount > viewModel.currentChunkInfo[viewModel.currentFileIndex] + 1) {
                            bufferedWriter.write(buffer, 0, actualLength)
                        }
                        else if (chunkCount == viewModel.currentChunkInfo[viewModel.currentFileIndex]) {
                            bufferedWriter.write(viewModel.text)
                        }

                        chunkCount++
                    }

                    bufferedReader.close()
                    bufferedWriter.close()
                }

                if (treeDocumentFile != null) {
                    var writeFileName = viewModel.fileReadInfo[viewModel.currentFileIndex].fileName
                    if (!writeFileName.startsWith("ntv_")) {
                        writeFileName = "ntv_$writeFileName"
                    }
                    var documentFile = treeDocumentFile.findFile(writeFileName)
                    documentFile?.delete()
                    documentFile = treeDocumentFile.createFile(
                        "text/*",
                        writeFileName
                    )

                    val fileUri = documentFile?.uri

                    if (fileUri != null) {
                        val outputStream = context.contentResolver.openOutputStream(fileUri)

                        if (outputStream != null) {
                            val bufferedReader = tempFile.bufferedReader(charset = viewModel.fileReadInfo[viewModel.currentFileIndex].charset)
                            val bufferedWriter = outputStream.bufferedWriter(charset = viewModel.fileReadInfo[viewModel.currentFileIndex].charset)

                            var chunkCount = 0
                            val skipSize = CHUNK_SIZE / 2

                            while (bufferedReader.ready()) {
                                val buffer = CharArray(CHUNK_SIZE)
                                val actualLength = bufferedReader.read(buffer, 0, skipSize)

                                bufferedWriter.write(buffer, 0, actualLength)

                                chunkCount++
                            }

                            bufferedReader.close()
                            bufferedWriter.close()

                            viewModel.fileReadInfo[viewModel.currentFileIndex].uri = fileUri
                        }
                    }
                }

                tempFile.delete()

                if (viewModel.loadingFlag) {
                    viewModel.loadingFlag = false
                    viewModel.loadingText = ""
                }

                readTextFlag = true
            }
        }
    }

    LaunchedEffect(readTextFlag) {
        if (readTextFlag) {
            val cursor = context.contentResolver.query(
                viewModel.fileReadInfo[viewModel.currentFileIndex].uri,
                null,
                null,
                null,
                null
            )
            if (cursor != null) {
                cursor.moveToFirst()
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                viewModel.fileReadInfo[viewModel.currentFileIndex].fileName =
                    cursor.getString(nameIndex)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                viewModel.fileReadInfo[viewModel.currentFileIndex].fileSize =
                    cursor.getInt(sizeIndex)
                cursor.close()
            }

            if (viewModel.fileReadInfo[viewModel.currentFileIndex].fileSize > 1000 * 1000) {
                val sizeMB = viewModel.fileReadInfo[viewModel.currentFileIndex].fileSize.toDouble() / 1000 / 1000
                viewModel.estimatedTime = sizeMB / 1000 * 160
                val loadingInfo =
                    "File size: " + "%.2f".format(sizeMB) + "MB. Take approx " + "%.2f".format(
                        viewModel.estimatedTime
                    ) + "s."
                viewModel.loadingText = loadingInfo
                val toast = Toast.makeText(context, loadingInfo, Toast.LENGTH_LONG)
                toast.show()
                viewModel.loadingFlag = true
            }

            readTextFlag = false

            withContext(Dispatchers.IO) {
                val stream = context.contentResolver.openInputStream(viewModel.fileReadInfo[viewModel.currentFileIndex].uri)
                if (stream == null && viewModel.gotoLine != -1) {
                    viewModel.gotoLine = -1
                }

                if (stream != null && viewModel.currentChunkInfo[viewModel.currentFileIndex] != -1) {
                    val bufferedReader = stream.bufferedReader(charset = viewModel.fileReadInfo[viewModel.currentFileIndex].charset)

                    var lineCount = 0
                    var chunkCount = 0
                    var currentIndex = -1
                    var matchCount = 0
                    val skipSize = CHUNK_SIZE / 2
                    var text: String

                    var readSize = skipSize
                    if (viewModel.findKeywordInfo.keyword.length > 0) {
                        readSize += viewModel.findKeywordInfo.keyword.length - 1
                    }
                    var foundFlag = false
                    var firstFlag = true

                    while (bufferedReader.ready()) {
                        bufferedReader.mark(readSize)
                        val buffer = CharArray(CHUNK_SIZE)
                        var actualLength = bufferedReader.read(buffer, 0, readSize)
                        text = String(buffer.sliceArray(0..<actualLength))
                        bufferedReader.reset()

                        var actualSkipSize = skipSize
                        if (actualLength < actualSkipSize) {
                            actualSkipSize = actualLength
                        }
                        var lines = text.substring(0..<actualSkipSize).lines()
                        val chunkLineNumber = lines.size

                        lines = text.lines()
                        if (viewModel.gotoLine != -1 && lineCount + lines.size >= viewModel.gotoLine) {
                            foundFlag = true
                        } else if (viewModel.findKeywordInfo.keyword != "") {
                            for (index in lines.indices) {
                                val count = countOccurrences(
                                    lines[index],
                                    viewModel.findKeywordInfo.keyword
                                )
                                matchCount += count
                                if (!foundFlag && lineCount + index >= viewModel.findKeywordInfo.startPos.line && count > 0) {
                                    var startOffset = 0
                                    if (lineCount + index == viewModel.findKeywordInfo.startPos.line) {
                                        startOffset = viewModel.findKeywordInfo.startPos.offset + 1
                                    }
                                    val offset = lines[index].indexOf(
                                        viewModel.findKeywordInfo.keyword,
                                        startOffset
                                    )
                                    if (offset != -1) {
                                        val afterMatchNumber = countOccurrences(
                                            lines[index].substring(startOffset..<lines[index].length),
                                            viewModel.findKeywordInfo.keyword
                                        )
                                        currentIndex = matchCount - afterMatchNumber + 1
                                        viewModel.keywordLine = lineCount + index
                                        viewModel.findKeywordStart =
                                            WordPos(lineCount + index, offset)
                                        foundFlag = true
                                    }
                                }
                            }
                        } else if (viewModel.gotoLine == -1 && chunkCount == viewModel.currentChunkInfo[viewModel.currentFileIndex]) {
                            foundFlag = true
                        }
                        if (foundFlag && firstFlag) {
                            viewModel.currentChunkInfo[viewModel.currentFileIndex] = chunkCount

                            viewModel.clearLineInfo()
                            bufferedReader.mark(CHUNK_SIZE)
                            actualLength = bufferedReader.read(buffer, 0, CHUNK_SIZE)
                            viewModel.text = String(buffer.sliceArray(0..<actualLength))
                            bufferedReader.reset()
                            lines = viewModel.text.lines()
                            for (line in lines) {
                                viewModel.addLineInfo(line, false)
                            }

                            viewModel.baseLineNumber = lineCount

                            firstFlag = false
                        }
                        bufferedReader.skip(skipSize.toLong())
                        // the number of line breaks (new lines)
                        lineCount += chunkLineNumber - 1
                        chunkCount++
                    }
                    if (foundFlag) {
                        viewModel.chunkNumberInfo[viewModel.currentFileIndex] = chunkCount
                        if (viewModel.gotoLine != -1) {
                            scrollFlag = true
                        }
                        if (viewModel.findKeywordInfo.keyword != "") {
                            viewModel.occurrenceInfo = OccurrenceInfo(currentIndex, matchCount)
                            scrollFlag = true
                        }
                        if (viewModel.keepPosLine != -1) {
                            if (viewModel.keepPosLine >= viewModel.baseLineNumber) {
                                viewModel.gotoLine = viewModel.keepPosLine
                            } else {
                                viewModel.gotoLine = viewModel.baseLineNumber
                            }
                            scrollFlag = true
                            viewModel.keepPosLine = -1
                        }
                    } else {
                        if (viewModel.gotoLine != -1) {
                            viewModel.gotoLine = -1
                        }
                        if (viewModel.findKeywordInfo.keyword != "") {
                            viewModel.occurrenceInfo = OccurrenceInfo(0, matchCount)
                        }
                    }
                    bufferedReader.close()
                }

                if (viewModel.loadingFlag) {
                    viewModel.loadingFlag = false
                    viewModel.loadingText = ""
                }
            }
        }
    }

    if (viewModel.loadingFlag) {
        Dialog(onDismissRequest = {}) {
            val currentTime = Date()
            val expectedTime = currentTime.time + 1000 * (viewModel.estimatedTime.toLong() + 1)
            val timeFormatter = SimpleDateFormat.getTimeInstance()
            val loadingDetail = "Loading..." + viewModel.loadingText + "\n" +
                    "Expected finish time: ${timeFormatter.format(expectedTime)}."
            TextField(
                value = loadingDetail,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(0.875F)
            )
        }
    }
    else {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    title = {
                        Row {
                            IconButton(
                                onClick = { viewModel.settingFlag = true },
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                Icon(Icons.Filled.Settings, null)
                            }
                        }
                    },
                    actions = {
                        if (viewModel.findFlag) {
                            var occurrenceText: @Composable() (() -> Unit)? = null
                            if (viewModel.occurrenceInfo != OccurrenceInfo(-1, 0)) {
                                occurrenceText = {
                                    Text("${viewModel.occurrenceInfo.currentIndex}/${viewModel.occurrenceInfo.matchNumber}")
                                }
                            }
                            TextField(
                                value = inputKeyword,
                                onValueChange = { it ->
                                    if (it.length <= KEYWORD_LENGTH) {
                                        inputKeyword = it
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = occurrenceText,
                                leadingIcon = {
                                    IconButton(
                                        onClick = {
                                            viewModel.findFlag = false
                                            viewModel.findKeywordStart = WordPos(-1, -1)
                                            viewModel.findKeywordInfo = FindKeywordInfo("", WordPos(-1, -1))
                                            viewModel.occurrenceInfo = OccurrenceInfo(-1, 0)
                                            inputKeyword = ""
                                        }
                                    ) {
                                        Icon(Icons.Filled.Close, null)
                                    }
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            if (viewModel.currentFileIndex != -1 && inputKeyword != "") {
                                                if (viewModel.findKeywordStart.line != -1) {
                                                    val currentKeywordStart =
                                                        viewModel.findKeywordStart
                                                    viewModel.findKeywordStart = WordPos(-1, -1)
                                                    viewModel.findKeywordInfo = FindKeywordInfo(
                                                        inputKeyword,
                                                        currentKeywordStart
                                                    )
                                                } else {
                                                    val lazyListState =
                                                        viewModel.lazyListStateInfo[viewModel.currentFileIndex]
                                                    val currentLine =
                                                        viewModel.baseLineNumber + lazyListState.firstVisibleItemIndex
                                                    var startLine = currentLine
                                                    if (inputKeyword == viewModel.findKeywordInfo.keyword) {
                                                        startLine = 0
                                                    }
                                                    viewModel.findKeywordInfo = FindKeywordInfo(
                                                        inputKeyword,
                                                        WordPos(startLine, -1)
                                                    )
                                                }
                                                readTextFlag = true
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Filled.Search, null)
                                    }
                                },
                                singleLine = true
                            )
                        } else if (viewModel.gotoFlag) {
                            TextField(
                                value = inputLine,
                                onValueChange = { it ->
                                    if (it.isDigitsOnly() && it.length <= 9) {
                                        inputLine = it
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = {
                                    Text("line")
                                },
                                leadingIcon = {
                                    IconButton(
                                        onClick = {
                                            viewModel.gotoFlag = false
                                            inputLine = "0"
                                        }
                                    ) {
                                        Icon(Icons.Filled.Close, null)
                                    }
                                },
                                trailingIcon = {
                                    Button(
                                        onClick = {
                                            viewModel.gotoFlag = false
                                            viewModel.gotoLine = inputLine.toInt() - 1
                                            if (viewModel.gotoLine < 0) {
                                                viewModel.gotoLine = 0
                                            }
                                            inputLine = "0"
                                            readTextFlag = true
                                        }
                                    ) {
                                        Text("Go")
                                    }
                                }
                            )
                        } else if (!viewModel.editFlag) {
                            val shorterWidth = min(configuration.screenWidthDp, configuration.screenHeightDp)
                            IconButton(
                                onClick = {
                                    newLauncher.launch(
                                        arrayOf(
                                            "text/*",
                                            "application/xml",
                                            "application/csv",
                                            "application/json",
                                            "application/yaml"
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(shorterWidth.dp / 12)
                            ) {
                                Icon(Icons.Filled.Add, null)
                            }
                            if (viewModel.currentFileIndex != -1) {
                                IconButton(
                                    onClick = {
                                        viewModel.menuFlag = true
                                    },
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(shorterWidth.dp / 12)
                                ) {
                                    Icon(Icons.Filled.Menu, null)
                                }
                                Button(
                                    onClick = {
                                        viewModel.charsetFlag = true
                                    },
                                    modifier = Modifier
                                        .fillMaxHeight()
                                ) {
                                    Text("encode")
                                }
                                IconButton(
                                    onClick = {
                                        viewModel.findFlag = true
                                    },
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(shorterWidth.dp / 12)
                                ) {
                                    Icon(Icons.Filled.Search, null)
                                }
                                Button(
                                    onClick = {
                                        viewModel.gotoFlag = true
                                    },
                                    modifier = Modifier
                                        .fillMaxHeight()
                                ) {
                                    Text("Go to line")
                                }
                                IconButton(
                                    onClick = {
                                        viewModel.collapseAll()
                                        viewModel.editFlag = true
                                    },
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(shorterWidth.dp / 12)
                                ) {
                                    Icon(Icons.Filled.Edit, null)
                                }
                            }
                        } else if (!viewModel.editLineNumberFlag) {
                            Button(
                                onClick = {
                                    viewModel.gotoLine =
                                        viewModel.baseLineNumber + viewModel.lazyListState.firstVisibleItemScrollOffset / textSizePx
                                    readTextFlag = true
                                    viewModel.editFlag = false
                                },
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                Text("discard")
                            }
                            Button(
                                onClick = {
                                    if (viewModel.treeUri == null) {
                                        treeLauncher.launch(null)
                                    }
                                    writeFlag = true
                                    viewModel.gotoLine =
                                        viewModel.baseLineNumber + viewModel.lazyListState.firstVisibleItemScrollOffset / textSizePx
                                    viewModel.editFlag = false
                                },
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                Text("save")
                            }
                            Button(
                                onClick = {
                                    treeLauncher.launch(null)
                                },
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                Text("saving place")
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        )
        { innerPadding ->
            if (viewModel.settingFlag) {
                Dialog(onDismissRequest = { viewModel.settingFlag = false }) {
                    LazyColumn {
                        item {
                            TextField(
                                value = HELP_INFO,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth(0.875F)
                            )
                        }
                        if (viewModel.currentFileIndex != -1 && !viewModel.editFlag) {
                            item {
                                Button(
                                    onClick = {
                                        viewModel.expandAll()
                                        viewModel.settingFlag = false
                                    },
                                    modifier = Modifier.fillMaxWidth(0.875F)
                                ) {
                                    Text("expand all lines")
                                }
                            }
                        } else if (viewModel.currentFileIndex != -1 && viewModel.editFlag) {
                            val lineNumberText = if (viewModel.editLineNumberFlag) "hide line number" else "show line number"
                            item {
                                Button(
                                    onClick = {
                                        viewModel.expandAll()
                                        viewModel.editLineNumberFlag = !viewModel.editLineNumberFlag
                                    },
                                    modifier = Modifier.fillMaxWidth(0.875F)
                                ) {
                                    Text(lineNumberText)
                                }
                            }
                        }
                    }
                }
            }

            if (viewModel.fileReadInfo.isEmpty()) {
                viewModel.menuFlag = false
            }
            if (viewModel.menuFlag) {
                Dialog(onDismissRequest = { viewModel.menuFlag = false }) {
                    LazyColumn {
                        itemsIndexed(viewModel.fileReadInfo) { index, item ->
                            Row {
                                Button(
                                    onClick = {
                                        viewModel.menuFlag = false
                                        viewModel.currentFileIndex = index
                                        readTextFlag = true
                                    },
                                    modifier = Modifier.fillMaxWidth(0.875F)
                                ) {
                                    Text(item.fileName)
                                    if (index == viewModel.currentFileIndex) {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            null
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        // switch to another available file when closing a file
                                        if (viewModel.fileReadInfo.size == 1) {
                                            viewModel.currentFileIndex = -1
                                        } else if (viewModel.currentFileIndex == viewModel.fileReadInfo.size - 1) {
                                            viewModel.currentFileIndex--
                                        } else if (index < viewModel.currentFileIndex) {
                                            viewModel.currentFileIndex--
                                        }
                                        viewModel.removeFileInfo(index)
                                        if (viewModel.currentFileIndex != -1) {
                                            readTextFlag = true
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        null
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (viewModel.charsetFlag) {
                Dialog(onDismissRequest = { viewModel.charsetFlag = false }) {
                    LazyColumn {
                        items(CHARSETS) { item ->
                            Button(
                                onClick = {
                                    viewModel.charsetFlag = false
                                    viewModel.fileReadInfo[viewModel.currentFileIndex].charset = item
                                    viewModel.gotoLine = 0
                                    readTextFlag = true
                                },
                                modifier = Modifier.fillMaxWidth(0.875F)
                            ) {
                                Text(item.displayName())
                                if (item === viewModel.fileReadInfo[viewModel.currentFileIndex].charset) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        null
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (viewModel.moveFlag) {
                Dialog(
                    onDismissRequest = {
                        viewModel.moveFlag = false
                        viewModel.moveForward = false
                        viewModel.moveBackward = false
                    }
                ) {
                    Column {
                        if (viewModel.moveBackward) {
                            Button(
                                onClick = {
                                    viewModel.moveFlag = false
                                    viewModel.moveBackward = false
                                    viewModel.moveForward = false
                                    val lazyListState = viewModel.lazyListStateInfo[viewModel.currentFileIndex]
                                    viewModel.keepPosLine = viewModel.baseLineNumber + lazyListState.firstVisibleItemIndex
                                    viewModel.currentChunkInfo[viewModel.currentFileIndex]--
                                    readTextFlag = true
                                },
                                modifier = Modifier.fillMaxWidth(0.875F)
                            ) {
                                Text("Load Previous")
                            }
                        }
                        if (viewModel.moveForward) {
                            Button(
                                onClick = {
                                    viewModel.moveFlag = false
                                    viewModel.moveBackward = false
                                    viewModel.moveForward = false
                                    val lazyListState = viewModel.lazyListStateInfo[viewModel.currentFileIndex]
                                    viewModel.keepPosLine = viewModel.baseLineNumber + lazyListState.firstVisibleItemIndex
                                    viewModel.currentChunkInfo[viewModel.currentFileIndex]++
                                    readTextFlag = true
                                },
                                modifier = Modifier.fillMaxWidth(0.875F)
                            ) {
                                Text("Load Next")
                            }
                        }
                    }
                }
            }

            if (viewModel.editFlag) {
                viewModel.lazyListState = rememberLazyListState()
                val focusRequester = remember { FocusRequester() }

                LazyRow(
                    modifier = Modifier.padding(innerPadding),
                ) {
                    item {
                        LazyColumn(
                            state = viewModel.lazyListState,
                        ) {
                            var displayText = viewModel.text
                            var readOnlyFlag = false
                            if (viewModel.editLineNumberFlag) {
                                val lines = viewModel.text.lines()
                                var finalText = ""
                                for (index in lines.indices) {
                                    val lineNumber = viewModel.baseLineNumber + index + 1
                                    finalText += lineNumber.toString() + " " + lines[index] + "\n"
                                }
                                displayText = finalText.slice(0..<finalText.length - 1)
                                readOnlyFlag = true
                            }
                            item {
                                BasicTextField(
                                    value = displayText,
                                    onValueChange = {
                                        viewModel.text = it
                                    },
                                    modifier = Modifier
                                        .defaultMinSize(
                                            configuration.screenWidthDp.dp,
                                            Dp.Unspecified
                                        )
                                        .focusRequester(focusRequester),
                                    textStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
                                    readOnly = readOnlyFlag
                                )
                                LaunchedEffect(Unit) {
                                    focusRequester.requestFocus()
                                    syncEditFlag = true
                                }
                            }
                        }
                        LaunchedEffect(syncEditFlag) {
                            if (syncEditFlag) {
                                syncEditFlag = false
                                val scrollOffset = textSizePx * viewModel.lazyListStateInfo[viewModel.currentFileIndex].firstVisibleItemIndex
                                coroutineScope.launch {
                                    viewModel.lazyListState.scrollBy(scrollOffset.toFloat())
                                }
                            }
                        }
                    }
                }
            } else if (viewModel.currentFileIndex != -1) {
                LazyRow(
                    modifier = Modifier.padding(innerPadding)
                ) {
                    item {
                        val lazyListState = viewModel.lazyListStateInfo[viewModel.currentFileIndex]

                        if (lazyListState.isScrollInProgress) {
                            if (!lazyListState.canScrollForward) {
                                if (viewModel.currentChunkInfo[viewModel.currentFileIndex] < viewModel.chunkNumberInfo[viewModel.currentFileIndex] - 1) {
                                    viewModel.moveForward = true
                                    viewModel.moveFlag = true
                                }
                            }
                            if (!lazyListState.canScrollBackward) {
                                if (viewModel.currentChunkInfo[viewModel.currentFileIndex] > 0) {
                                    viewModel.moveBackward = true
                                    viewModel.moveFlag = true
                                }
                            }
                        }

                        LazyColumn(state = lazyListState) {
                            itemsIndexed(viewModel.lines) { index, item ->
                                var expandEnable = true
                                if (item.length >= WRAP_LENGTH) {
                                    expandEnable = false
                                    viewModel.expandedList[index] = true
                                }
                                var textModifier = Modifier
                                    .defaultMinSize(
                                        configuration.screenWidthDp.dp,
                                        textSizeDp
                                    )
                                if (viewModel.expandedList[index]) {
                                    textModifier = textModifier.width(configuration.screenWidthDp.dp)
                                } else {
                                    textModifier = textModifier.height(textSizeDp)
                                }
                                Row(
                                    modifier = textModifier
                                ) {
                                    LineLeadingIcon(
                                        index,
                                        expandEnable,
                                        viewModel
                                    )
                                    Box {
                                        if (viewModel.findKeywordStart.line == viewModel.baseLineNumber + index) {
                                            val endOffset = viewModel.findKeywordStart.offset + viewModel.findKeywordInfo.keyword.length - 1
                                            val keywordLine = TextFieldValue(
                                                item,
                                                TextRange(
                                                    viewModel.findKeywordStart.offset,
                                                    endOffset + 1
                                                )
                                            )
                                            BasicTextField(
                                                value = keywordLine,
                                                onValueChange = {},
                                                readOnly = true,
                                                modifier = Modifier
                                                    .defaultMinSize(
                                                        Dp.Unspecified,
                                                        textSizeDp
                                                    )
                                                    .wrapContentHeight(),
                                                textStyle = TextStyle(fontSize = 16.sp)
                                            )
                                        } else {
                                            BasicTextField(
                                                value = item,
                                                onValueChange = {},
                                                readOnly = true,
                                                modifier = Modifier
                                                    .defaultMinSize(
                                                        Dp.Unspecified,
                                                        textSizeDp
                                                    )
                                                    .wrapContentHeight(),
                                                textStyle = TextStyle(fontSize = 16.sp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        LaunchedEffect(scrollFlag) {
                            if (scrollFlag) {
                                scrollFlag = false
                                coroutineScope.launch {
                                    if (viewModel.gotoLine != -1) {
                                        lazyListState.scrollToItem(viewModel.gotoLine - viewModel.baseLineNumber)
                                        viewModel.gotoLine = -1
                                    }
                                    if (viewModel.keywordLine != -1) {
                                        lazyListState.scrollToItem(viewModel.keywordLine - viewModel.baseLineNumber)
                                        viewModel.keywordLine = -1
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ViewerPreview() {
    NewTextViewerTheme {
        Main()
    }
}