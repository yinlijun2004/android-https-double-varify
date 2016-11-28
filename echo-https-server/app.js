const tls = require('tls');
const fs = require('fs');

const options = {
  pfx: fs.readFileSync('./server.pfx'),
  passphrase: "123456",
  // This is necessary only if using the client certificate authentication.
  requestCert: true,

};

const server = tls.createServer(options, (socket) => {
  console.log('server connected',
              socket.authorized ? 'authorized' : 'unauthorized');
  socket.setEncoding('utf8');
  socket.on('data', (data) => {
      console.log(data);
      socket.write("you said:" + data);
      //socket.pipe(socket);
  });
  socket.on('end', (socket) => {
    console.log("socket closed");
  });
});
server.listen(8000, () => {
  console.log('server bound');
});



