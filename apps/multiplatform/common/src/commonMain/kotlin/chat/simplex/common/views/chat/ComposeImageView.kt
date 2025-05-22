package chat.simplex.common.views.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import androidx.compose.ui.unit.dp
import chat.simplex.common.platform.base64ToBitmap
import chat.simplex.res.MR
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.helpers.UploadContent

@Composable
fun ComposeImageView(media: ComposePreview.MediaPreview, cancelImages: () -> Unit, cancelEnabled: Boolean) {
  val sentColor = MaterialTheme.appColors.sentMessage
  Row(
    Modifier
      .padding(top = 8.dp)
      .background(sentColor),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    LazyRow(
      Modifier.weight(1f).padding(start = DEFAULT_PADDING_HALF, end = if (cancelEnabled) 0.dp else DEFAULT_PADDING_HALF),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(DEFAULT_PADDING_HALF),
    ) {
      itemsIndexed(media.images) { index, item ->
        val content = media.content[index]
        if (content is UploadContent.Video) {
          Box(contentAlignment = Alignment.Center) {
            val imageBitmap = base64ToBitmap(item)
            Image(
              imageBitmap,
              "preview video",
              modifier = Modifier.widthIn(max = 80.dp).height(60.dp)
            )
            Icon(
              painterResource(MR.images.ic_videocam_filled),
              "preview video",
              Modifier
                .size(20.dp),
              tint = Color.White
            )
          }
        } else {
          val imageBitmap = base64ToBitmap(item)
          Image(
            imageBitmap,
            "preview image",
            modifier = Modifier.widthIn(max = 80.dp).height(60.dp)
          )
        }
      }
    }
    if (cancelEnabled) {
      IconButton(onClick = cancelImages) {
        Icon(
          painterResource(MR.images.ic_close),
          contentDescription = stringResource(MR.strings.icon_descr_cancel_image_preview),
          tint = MaterialTheme.colors.primary,
        )
      }
    }
  }
}
