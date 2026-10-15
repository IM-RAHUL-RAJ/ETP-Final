// Same shape as the auth service: an express-style http server with socket.io attached.
const http = require('http');
const { Server } = require('socket.io');
const server = http.createServer((req, res) => { res.writeHead(200, {'Content-Type':'application/json'}); res.end('{"ok":true}'); });
new Server(server, { path: '/socket.io' });
server.listen(4777, () => console.log('listening'));
