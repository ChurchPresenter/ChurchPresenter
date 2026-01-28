package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.awt.print.Book

@Composable
fun SelectionList(
    modifier: Modifier = Modifier,
    list: List<String>,
    selectedIndex: Int = 0,
    onItemSelected: (String) -> Unit
) {
    var selectedItem by rememberSaveable { mutableStateOf(list.getOrNull(selectedIndex)) }
    val height = 450.dp
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(Color.White)
            .padding(4.dp)
    ) {
        items(list) { item ->
            val isSelected = item == selectedItem
            Text(
                text = item,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isSelected) Color.Cyan else Color.White)
                    .clickable {
                        selectedItem = item
                        onItemSelected.invoke(item)
                    }
                    .padding(6.dp),
                color = Color.Black
            )
        }
    }
}