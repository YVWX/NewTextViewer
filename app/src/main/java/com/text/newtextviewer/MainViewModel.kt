package com.text.newtextviewer

import android.net.Uri
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.nio.charset.Charset

data class FileReadInfo(
    val uri: Uri,
    var fileName: String,
    var charset: Charset
)

data class WordPos(
    val line: Int,
    val offset: Int
)

data class FindKeywordInfo(
    val keyword: String,
    val startPos: WordPos
)

data class OccurrenceInfo(
    val currentIndex: Int,
    val matchNumber: Int
)

class TextViewerViewModel : ViewModel() {
    val fileReadInfo = mutableStateListOf<FileReadInfo>()

    val lazyListStateInfo = arrayListOf<LazyListState>()

    var currentChunkInfo = arrayListOf<Int>()

    var chunkNumberInfo = arrayListOf<Int>()

    val lines = mutableStateListOf<String>()

    val expandedList = mutableStateListOf<Boolean>()

    var findKeywordInfo by mutableStateOf(FindKeywordInfo("", WordPos(-1, -1)))

    var baseLineNumber by mutableIntStateOf(0)

    var currentFileIndex by mutableIntStateOf(-1)

    var findKeywordStart = WordPos(-1, -1)

    var helpFlag by mutableStateOf(false)

    var menuFlag by mutableStateOf(false)

    var charsetFlag by mutableStateOf(false)

    var findFlag by mutableStateOf(false)

    var gotoFlag by mutableStateOf(false)

    var moveFlag by mutableStateOf(false)

    var moveForward by mutableStateOf(false)

    var moveBackward by mutableStateOf(false)

    var keywordLine = -1

    var keepPosLine = -1

    var gotoLine = -1

    var occurrenceInfo by mutableStateOf(OccurrenceInfo(-1, 0))

    fun addLineInfo(line: String, expanded: Boolean) {
        lines.add(line)
        expandedList.add(expanded)
    }

    fun clearLineInfo() {
        lines.clear()
        expandedList.clear()
    }

    fun expandAll() {
        for (index in expandedList.indices) {
            expandedList[index] = true
        }
    }

    fun collapseAll() {
        for (index in expandedList.indices){
            expandedList[index] = false
        }
    }

    fun removeFileInfo(index: Int) {
        fileReadInfo.removeAt(index)
        lazyListStateInfo.removeAt(index)
        currentChunkInfo.removeAt(index)
        chunkNumberInfo.removeAt(index)
    }
}