const { createProxyMiddleware } = require('http-proxy-middleware');

module.exports = function(app) { 

    const filterNonHotReloadWsRoutes = function (pathname, req) {
        // webpack hot reload wwebsockets route is exactly /ws. no need to proxy that
        return pathname != "/ws" && pathname.includes("/ws/");
      };
        app.use(
        createProxyMiddleware(filterNonHotReloadWsRoutes, {
            target: 'ws://localhost:8080',
            ws: true
        })
    );
};