package io.github.niteshprasad.reelune.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.niteshprasad.reelune.data.MovieFilters
import io.github.niteshprasad.reelune.ui.theme.Reel
import java.time.Year

private data class Choice(val label: String, val hint: String? = null, val group: String? = null, val apply: (MovieFilters) -> MovieFilters, val selected: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(kind: FilterKind, state: HomeState, onDismiss: () -> Unit, onPick: (MovieFilters) -> Unit) {
    val f = state.filters
    val title: String
    val choices: List<Choice>
    when (kind) {
        FilterKind.GENRE -> {
            title = "Genre"
            choices = listOf(Choice("All genres", apply = { it.copy(genreId = null) }, selected = f.genreId == null)) +
                state.genres.map { g -> Choice(g.name, apply = { it.copy(genreId = g.id) }, selected = f.genreId == g.id) }
        }
        FilterKind.YEAR -> {
            title = "Release year"
            val now = Year.now().value
            choices = listOf(Choice("Any year", apply = { it.copy(year = "") }, selected = f.year.isEmpty())) +
                decadeOptions().map { (v, l) -> Choice(l, group = "Periods", apply = { it.copy(year = v) }, selected = f.year == v) } +
                (now downTo 1920).map { y -> Choice("$y", group = "Years", apply = { it.copy(year = "$y") }, selected = f.year == "$y") }
        }
        FilterKind.LANGUAGE -> {
            title = "Original language"
            val popular = listOf("en", "hi", "ko", "ja", "fr", "es", "ta", "te", "ml", "zh", "it", "de")
            val byCode = state.languages.associateBy { it.code }
            choices = listOf(Choice("Any language", apply = { it.copy(language = "") }, selected = f.language.isEmpty())) +
                popular.mapNotNull { byCode[it] }.map { l -> Choice(l.englishName, group = "Common", apply = { it.copy(language = l.code) }, selected = f.language == l.code) } +
                state.languages.map { l -> Choice(l.englishName, hint = l.name.takeIf { n -> n.isNotBlank() && n != l.englishName }, group = "All languages", apply = { it.copy(language = l.code) }, selected = f.language == l.code) }
        }
        FilterKind.SORT -> {
            title = "Sort by"
            choices = state.sorts.map { s -> Choice(s.label, apply = { it.copy(sort = s) }, selected = f.sort == s) }
        }
        FilterKind.RATING -> {
            title = "Minimum rating"
            choices = listOf(Choice("Any rating", apply = { it.copy(minRating = 0) }, selected = f.minRating == 0)) +
                (8 downTo 5).map { r -> Choice("$r+", hint = "out of 10", apply = { it.copy(minRating = r) }, selected = f.minRating == r) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = kind != FilterKind.YEAR && kind != FilterKind.LANGUAGE),
        containerColor = androidx.compose.ui.graphics.Color(0xFF17122F),
        contentColor = Reel.Text,
        scrimColor = Reel.Night.copy(alpha = 0.55f),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Reel.Text, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp).navigationBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
            var prev: String? = null
            choices.forEachIndexed { i, c ->
                if (c.group != null && c.group != prev) {
                    item(key = "h$i") { GroupHeader(c.group) }
                }
                prev = c.group
                item(key = "c$i") { ChoiceRow(c, onClick = { onPick(c.apply(f)) }) }
            }
        }
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Reel.TextFaint, modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp))
}

@Composable
private fun ChoiceRow(c: Choice, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(if (c.selected) Modifier.background(Reel.Moonglow.copy(alpha = 0.1f)) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(c.label, color = if (c.selected) Reel.Moonglow else Reel.Text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        c.hint?.let { Text(it, color = Reel.TextFaint, style = MaterialTheme.typography.bodySmall) }
        if (c.selected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Reel.Moonglow)
    }
}
