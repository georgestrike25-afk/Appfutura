// Renderiza las láminas y cada pantalla de pantallas.html a PNG (design/previews).
const {chromium}=require('playwright');
(async()=>{
 const b=await chromium.launch();
 const p=await b.newPage({viewport:{width:1560,height:1000},deviceScaleFactor:2});
 await p.goto('file://'+__dirname+'/pantallas.html',{waitUntil:'networkidle'});
 await p.waitForSelector('body[data-ready="1"]',{timeout:120000});
 await p.waitForTimeout(500);
 const boards=[['b-inicio','02-inicio-mapa-rutas'],['b-import','03-importacion-y-detalle'],['b-nav','04-navegacion'],['b-offline','05-mapas-offline-ajustes-oscuro'],['b-estados','06-estados']];
 for(const [id,n] of boards){await (await p.$('#'+id)).screenshot({path:`../previews/${n}.png`})}
 const screens=['s-inicio','s-mapa','s-rutas','s-import','s-import-error','s-detalle','s-nav','s-pausa','s-fin','s-offline','s-ajustes','s-inicio-oscuro','s-vacio','s-permiso','s-sinmapa'];
 require('fs').mkdirSync('../previews/pantallas',{recursive:true});
 let i=1;for(const id of screens){await (await p.$('#'+id)).screenshot({path:`../previews/pantallas/${String(i++).padStart(2,'0')}-${id.slice(2)}.png`})}
 await b.close();
})();
