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
        <a href="/">Home</a> | Layer ${layer.name}
    </div>

    <div class="grd">
        <div class="grd-row">
            <div class="grd-row-col-2-6 metadata">
                <h4 class="fnt--green">Metadata</h4>
                <div>Name = ${layer.name}</div>
                <div>Projection = ${layer.proj}</div>
                <div>Count = ${layer.getCount()}</div>
                <div>Bounds = ${layer.getBounds().minX}, ${layer.getBounds().minY}, ${layer.getBounds().maxX}, ${layer.getBounds().maxY} ${layer.getBounds().proj}</div>
            </div>
            <div class="grd-row-col-4-6" webservices>
                <h4 class="fnt--green webservices">Web Services</h4>
                <div><a href="/layers/${layer.name}/schema">Schema</a></div>
                <div><a href="/layers/${layer.name}/count">Count</a></div>
                <div><a href="/layers/${layer.name}/bounds">Bounds</a></div>
                <div><a href="/layers/${layer.name}/features.json">Features (GeoJson)</a></div>
                <div><a href="/layers/${layer.name}/features.csv">Features (CSV)</a></div>
                <div><a href="/layers/${layer.name}/gdal">QGIS Layer</a></div>
            </div>
            <div class="grd-row-col-5-6 fields">
                <h4 class="fnt--green">Fields</h4>
                <div style="max-height: 200px; overflow: scroll;">
                    <table>
                        <thead>
                            <tr>
                                <th>Name</th>
                                <th>Type</th>
                            </tr>
                        </thead>
                        <tbody>
                        <#list layer.schema.fields as field>
                            <tr>
                                <td>${field.name}</td>
                                <td>${field.typ}</td>
                            </tr>
                        </#list>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
        <div class="grd-row">
            <div class="grd-row-col-4-6 map">
                <h4 class="fnt--green">Map</h4>
                <div id="map" class="map"></div>
            </div>
            <div class="grd-row-col-6-6 values">
                <h4 class="fnt--green">Values</h4>
                <div id="selected" style="max-width: 400px; max-height: 500px; overflow: scroll;">
                </div>
            </div>
        </div>
    </div>

    <script type="text/javascript">
      var map = new ol.Map({
        target: 'map',
        layers: [
          new ol.layer.Tile({
            source: new ol.source.OSM()
          }),
          new ol.layer.Vector({
            source: new ol.source.Vector({
                 format: new ol.format.GeoJSON(),
                 url: '/layers/${layer.name}/features.json'
            })
          })
        ],
        view: new ol.View({
          center: ol.proj.fromLonLat([37.41, 8.82]),
          zoom: 1
        })
      });
      var select = new ol.interaction.Select();
      map.addInteraction(select);
      select.on('select', function(e) {
         var elem = document.getElementById("selected");
         var html = "";
         e.target.getFeatures().forEach(function(f) {
            var props = f.getProperties();
            html += "<table><thead><th>Name</th><th>Value</th></thead><tbody>";
            for (prop in props) {
                html += "<tr><td>" + prop + "</td><td>" + props[prop] + "</td></tr>";
            }
            html += "</tbody></table>";
         })
         elem.innerHTML = html;
      });
    </script>

</body>