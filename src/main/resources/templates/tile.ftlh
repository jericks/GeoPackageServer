<!DOCTYPE HTML>
<html>
<head>
    <title>GeoPackage Server</title>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <link rel="stylesheet" href="/furtive.min.css" type="text/css">
    <link rel="stylesheet" href="/ol.css" type="text/css">
    <style>
        body {
            margin-left: 20px;
        }
        .map {
            height: 500px;
            width: 100%;
            padding-right: 20px;
        }
    </style>
    <script src="/ol.js" type="text/javascript"></script>
</head>
<body>

    <h1 class="fnt--blue">GeoPackage Server</h1>

    <div style="margin-left: 1rem;">
        <a href="/">Home</a> | Tile ${name}
    </div>

    <div class="grd">
        <div class="grd-row">
            <div class="grd-row-col-2-6 metadata">
                <h4 class="fnt--green">Metadata</h4>
                <div>Name = ${name}</div>
                <div>Projection = ${projection}</div>
                <div>Bounds = ${bounds.minX}, ${bounds.minY}, ${bounds.maxX}, ${bounds.maxY} ${bounds.proj}</div>
                <div>Tile Size = ${pyramid.tileWidth} x ${pyramid.tileHeight}</div>
            </div>
            <div class="grd-row-col-4-6 tilestats">
                <h4 class="fnt--green">Tile Stats</h4>
                <table>
                    <thead>
                        <th>Zoom Level</th>
                        <th>Total # Tiles</th>
                        <th># Tiles</th>
                        <th>Percent</th>
                    </thead>
                    <tbody>
                    <#list stats as stat>
                        <tr>
                            <td>${stat.zoom}</td>
                            <td>${stat.total}</td>
                            <td>${stat.tiles}</td>
                            <td>${stat.percent?string.percent}</td>
                        </tr>
                    </#list>
                    </tbody>
                </table>
            </div>
            <div class="grd-row-col-5-6">
                <h4 class="fnt--green webservices">Web Services</h4>
                <div>
                    <div><a href="/tiles/${name}/pyramid">Pyramid</a></div>
                    <div><a href="/tiles/${name}/tile/0/0/0">Tiles</a></div>
                    <div><a href="/tiles/${name}/raster/400/400/-156.533203,3.688855,-50.712891,56.800878">Raster</a></div>
                    <div><a href="/tiles/${name}/tile/counts">Tile Counts</a></div>
                    <div><a href="/tiles/${name}/gdal">QGIS Layer</a></div>
                </div>
            </div>
        </div>
        <div>
            <div class="grd-row">
                <div class="grd-row-col-3-6 map">
                    <h4 class="fnt--green">Map</h4>
                    <div id="map" class="map"></div>
                </div>
                <div class="grd-row-col-3-6 pyramid">
                    <h4 class="fnt--green">Pyramid</h4>
                    <table>
                        <thead>
                        <th>Zoom</th>
                        <th>Columns</th>
                        <th>Rows</th>
                        <th>Tiles</th>
                        <th>X Resolution</th>
                        <th>Y Resolution</th>
                        </thead>
                        <tbody>
                        <#list pyramid.grids as grid>
                            <tr>
                                <td>${grid.z}</td>
                                <td>${grid.width}</td>
                                <td>${grid.height}</td>
                                <td>${grid.size}</td>
                                <td>${grid.xResolution?c}</td>
                                <td>${grid.yResolution?c}</td>
                            </tr>
                        </#list>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    </div>

    <script type="text/javascript">
      var map = new ol.Map({
        target: 'map',
        layers: [
          new ol.layer.Tile({
            source: new ol.source.XYZ({
              url: '/tiles/${name}/tile/{z}/{x}/{y}',
              projection: '${projection}',
              tileGrid: new ol.tilegrid.TileGrid({
                extent: [${bounds.minX?c},${bounds.minY?c},${bounds.maxX?c},${bounds.maxY?c}],
                minZoom: ${minZoom},
                maxZoom: ${maxZoom},
                tileSize: [${pyramid.tileWidth},${pyramid.tileHeight}],
                resolutions: [
                <#list pyramid.grids as grid>
                    ${grid.xResolution?c}<#if grid_has_next>,</#if>
                </#list>
                ]
              })
            })
          })
        ],
        view: new ol.View({
          center: ol.proj.fromLonLat([37.41, 8.82]),
          zoom: 1
        })
      });
    </script>

</body>
</html>