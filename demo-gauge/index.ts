import * as express from 'express';
import * as path from 'path';
import * as Influx from 'influx';

const app = express();

var port = process.env.PORT || process.env.OPENSHIFT_NODEJS_PORT || 8080,
  ip = process.env.IP || process.env.OPENSHIFT_NODEJS_IP || '0.0.0.0';

var influxdbhost = process.env.INFLUXDB_PORT_8086_TCP_ADDR || 'influxdb.hono.svc',
  influxdbport = process.env.INFLUXDB_PORT_8086_TCP_PORT || 8086;

app.use('/gauge', express.static(__dirname + '/../node_modules/gaugeJS/dist/'));
app.use('/jquery', express.static(__dirname + '/../node_modules/jquery/dist/'));

app.engine('html', require('ejs').renderFile);

const influx = new Influx.InfluxDB('http://' + influxdbhost + ':' + influxdbport + '/payload')

influx.getDatabaseNames()
  .then(names => { console.log(names) });

app.get('/', function (req, res) {
  res.render('index.html');
});

app.get('/power_consumption', function (req, res) {

  influx.query(`
    SHOW TAG VALUES WITH KEY ="device_id"
  `).then(rows => {
    influx.query(`
      select * from P where device_id = '`+ rows[rows.length-1]['value'] + `'
      order by time desc
      limit 1
    `).then(rows => {
        rows.forEach(row => res.json(row))
    })
  })
})


/* istanbul ignore next */
if (!module.parent) {
  app.listen(port, ip);
  console.log('Express listening on http://' + ip + ':' + port);
}
