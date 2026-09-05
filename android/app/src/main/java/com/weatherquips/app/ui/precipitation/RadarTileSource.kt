package com.weatherquips.app.ui.precipitation

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex

/**
 * A RainViewer radar frame as an osmdroid tile source.
 *
 * This is the native equivalent of the web app's Leaflet tile layer: the same
 * `/256/{z}/{x}/{y}/2/1_1.png` tiles, served straight into the map's tile
 * pipeline (so they are disk-cached and reused as the animation loops) instead
 * of being redrawn by a browser.
 */
class RadarTileSource(
    frameName: String,
    private val urlTemplate: String,
) : OnlineTileSourceBase(
    /* aName = */ frameName,
    /* aZoomMinLevel = */ 0,
    /* aZoomMaxLevel = */ MAX_RADAR_ZOOM,
    /* aTileSizePixels = */ 256,
    /* aFilenameEnding = */ ".png",
    /* aBaseUrl = */ arrayOf("https://tilecache.rainviewer.com/"),
    /* pCopyright = */ "© RainViewer",
) {

    override fun getTileURLString(pMapTileIndex: Long): String = urlTemplate
        .replace("{z}", MapTileIndex.getZoom(pMapTileIndex).toString())
        .replace("{x}", MapTileIndex.getX(pMapTileIndex).toString())
        .replace("{y}", MapTileIndex.getY(pMapTileIndex).toString())

    companion object {
        /** RainViewer only renders radar up to z12; the map may zoom in further. */
        const val MAX_RADAR_ZOOM = 12
    }
}

/** Base map: standard OpenStreetMap tiles, free and key-free. */
fun baseTileSource() = TileSourceFactory.MAPNIK
