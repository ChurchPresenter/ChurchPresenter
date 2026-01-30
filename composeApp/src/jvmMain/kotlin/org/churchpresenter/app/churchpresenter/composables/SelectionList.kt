package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.logging.Logger

private val logger = Logger.getLogger("SelectionList")

@Composable
fun SelectionList(
    modifier: Modifier = Modifier,
    list: List<String>,
    selectedIndex: Int = 0,
    onItemSelected: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val height = 450.dp

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .height(height)
            .background(Color.White)
            .padding(4.dp)
    ) {
        itemsIndexed(
            items = list,
            key = { index, _ -> "$index-${list.hashCode()}" }
        ) { index, item ->
            val isSelected = index == selectedIndex
            Text(
                text = item,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isSelected) Color.Cyan else Color.White)
                    .clickable {
                        logger.info("SelectionList: item clicked - index=$index, item=$item")
                        onItemSelected.invoke(item)
                    }
                    .padding(6.dp),
                color = Color.Black
            )
        }
    }
}