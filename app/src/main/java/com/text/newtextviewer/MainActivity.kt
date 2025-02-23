package com.text.newtextviewer

import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.text.isDigitsOnly
import androidx.lifecycle.viewmodel.compose.viewModel
import com.text.newtextviewer.ui.theme.NewTextViewerTheme
import kotlinx.coroutines.launch

const val CHUNK_SIZE = 1048576

const val KEYWORD_LENGTH = 1024

const val WRAP_LENGTH = 1024

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

const val HELP_INFO = "The lines too long will be wrapped automatically and they cannot be unwrapped"


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
    Row {
        Box {
            Text(
                text = lineNumber.toString(),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .defaultMinSize(50.dp, 50.dp)
                    .wrapContentHeight()
            )
        }
        IconButton(
            onClick = {
                viewModel.expandedList[index] = !viewModel.expandedList[index]
            },
            modifier = Modifier.height(50.dp),
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

    var scrollFlag by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) {
            val cursor = context.contentResolver.query(it, null, null, null, null)
            var fileName = ""
            if (cursor != null) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                fileName = cursor.getString(nameIndex)
                cursor.close()
            }

            viewModel.fileReadInfo.add(FileReadInfo(it, fileName, Charsets.UTF_8))
            viewModel.currentChunkInfo.add(0)
            viewModel.chunkNumberInfo.add(0)
            viewModel.currentFileIndex = viewModel.fileReadInfo.size - 1
            newFileFlag = true
        }
    }

    if (newFileFlag) {
        viewModel.lazyListStateInfo.add(rememberLazyListState())
        newFileFlag = false
        readTextFlag = true
    }

    Scaffold (
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Row {
                        IconButton(
                            onClick = {
                                viewModel.helpFlag = true
                            },
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Icon(Icons.Filled.Info, null)
                        }
                        if (viewModel.currentFileIndex != -1) {
                            Button(
                                onClick = {
                                    viewModel.expandAll()
                                },
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                Text("expand all")
                            }
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
                                                val currentKeywordStart = viewModel.findKeywordStart
                                                viewModel.findKeywordStart = WordPos(-1, -1)
                                                viewModel.findKeywordInfo = FindKeywordInfo(
                                                    inputKeyword,
                                                    currentKeywordStart
                                                )
                                            }
                                            else {
                                                val lazyListState = viewModel.lazyListStateInfo[viewModel.currentFileIndex]
                                                val currentLine = viewModel.baseLineNumber + lazyListState.firstVisibleItemIndex
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
                    }
                    else if (viewModel.gotoFlag) {
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
                    }
                    else {
                        IconButton(
                            onClick = {
                                launcher.launch(
                                    arrayOf(
                                        "text/*",
                                        "application/xml",
                                        "application/csv",
                                        "application/json",
                                        "application/yaml"
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Icon(Icons.Filled.Add, null)
                        }
                        if (viewModel.currentFileIndex != -1) {
                            IconButton(
                                onClick = {
                                    viewModel.menuFlag = true
                                },
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                Icon(Icons.Filled.Menu, null)
                            }
                            Button(
                                onClick = {
                                    viewModel.charsetFlag = true
                                },
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                Text("encoding")
                            }
                            IconButton(
                                onClick = {
                                    viewModel.findFlag = true
                                },
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                Icon(Icons.Filled.Search, null)
                            }
                            Button(
                                onClick = {
                                    viewModel.gotoFlag = true
                                },
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                Text("Go to line")
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    )
    { innerPadding ->
        if (viewModel.helpFlag) {
            Dialog(onDismissRequest = { viewModel.helpFlag = false }) {
                LazyColumn {
                    item {
                        TextField(
                            value = HELP_INFO,
                            onValueChange = {},
                            readOnly = true
                        )
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
                                    }
                                    else if (viewModel.currentFileIndex == viewModel.fileReadInfo.size - 1) {
                                        viewModel.currentFileIndex--
                                    }
                                    else if (index < viewModel.currentFileIndex) {
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

        if (readTextFlag) {
            val stream = context.contentResolver.openInputStream(viewModel.fileReadInfo[viewModel.currentFileIndex].uri)
            if (stream == null && viewModel.gotoLine != -1) {
                viewModel.gotoLine = -1
            }
            readTextFlag = false

            if (stream != null && viewModel.currentChunkInfo[viewModel.currentFileIndex] != -1) {
                val bufferedReader = stream.bufferedReader(charset = viewModel.fileReadInfo[viewModel.currentFileIndex].charset)

                var lineCount = 0
                var chunkCount = 0
                var currentIndex = -1
                var matchCount = 0
                val skipSize = CHUNK_SIZE / 2

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
                    var text = String(buffer.sliceArray(0..<actualLength))
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
                    }
                    else if (viewModel.findKeywordInfo.keyword != "") {
                        for (index in lines.indices) {
                            val count = countOccurrences(lines[index], viewModel.findKeywordInfo.keyword)
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
                                    viewModel.findKeywordStart = WordPos(lineCount + index, offset)
                                    foundFlag = true
                                }
                            }
                        }
                    }
                    else if (viewModel.gotoLine == -1 && chunkCount == viewModel.currentChunkInfo[viewModel.currentFileIndex]) {
                        foundFlag = true
                    }
                    if (foundFlag && firstFlag) {
                        viewModel.currentChunkInfo[viewModel.currentFileIndex] = chunkCount

                        viewModel.clearLineInfo()
                        bufferedReader.mark(CHUNK_SIZE)
                        actualLength = bufferedReader.read(buffer, 0, CHUNK_SIZE)
                        text = String(buffer.sliceArray(0..<actualLength))
                        bufferedReader.reset()
                        lines = text.lines()

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
                        }
                        else {
                            viewModel.gotoLine = viewModel.baseLineNumber
                        }
                        scrollFlag = true
                        viewModel.keepPosLine = -1
                    }
                }
                else {
                    if (viewModel.gotoLine != -1) {
                        viewModel.gotoLine = -1
                    }
                    if (viewModel.findKeywordInfo.keyword != "") {
                        viewModel.occurrenceInfo = OccurrenceInfo(0, matchCount)
                    }
                }
                bufferedReader.close()
            }
        }

        if (viewModel.currentFileIndex != -1) {
            val coroutineScope = rememberCoroutineScope()

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
                                    50.dp
                                )
                            if (viewModel.expandedList[index]) {
                                textModifier = textModifier.width(configuration.screenWidthDp.dp)
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
                                                    50.dp
                                                )
                                                .wrapContentHeight()
                                        )
                                    } else {
                                        BasicTextField(
                                            value = item,
                                            onValueChange = {},
                                            readOnly = true,
                                            modifier = Modifier
                                                .defaultMinSize(
                                                    Dp.Unspecified,
                                                    50.dp
                                                )
                                                .wrapContentHeight()
                                        )
                                    }
                                }
                            }
                        }
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

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    NewTextViewerTheme {
        Main()
    }
}