# Diseño de Cumbre

Propuesta visual v2 (pendiente de aprobación). Nada de esto modifica todavía la app.

- `previews/`: láminas renderizadas (icono, inicio/mapa/rutas, importación, navegación, mapas offline y ajustes, estados).
- `previews/pantallas/`: cada pantalla por separado, a tamaño de teléfono (412 × 915 dp, 2×).
- `fuente/`: fuentes editables. `icono-v2.svg` y `icono-v2-monocromo.svg` son el icono propuesto; `pantallas.html` genera los mockups y `render.js` los exporta con Playwright (`node render.js`).

Los fondos de mapa de los mockups son ilustrativos (terreno generado por código con el estilo de Mapsforge); en la app los dibuja Mapsforge a partir del mapa offline instalado.
