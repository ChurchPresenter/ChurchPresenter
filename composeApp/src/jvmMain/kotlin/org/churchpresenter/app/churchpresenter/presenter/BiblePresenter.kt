package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.models.SelectedVerse

@Composable
fun BiblePresenter(
    modifier: Modifier = Modifier,
    selectedVerses: List<SelectedVerse>
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column {
            // Display each verse (primary and secondary if available)
            selectedVerses.forEach { verse ->
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        style = MaterialTheme.typography.titleLarge,
                        text = verse.verseText,
                        color = Color.White
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        style = MaterialTheme.typography.bodyMedium,
                        text = "${verse.bookName} ${verse.chapter}:${verse.verseNumber}",
                        color = Color.White
                    )
                }
            }
        }
    }
}