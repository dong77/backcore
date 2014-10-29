/**
 *Copyright 2014 Coinport Inc. All Rights Reserved.
 *Author: YangLi--ylautumn84@gmail.com
 *Filename: logger.js
 *Description:
 */

//'BTC' : 1000,
//'LTC' : 1010,
//'PTS' : 1200,
//'DOGE' : 1100

//TRACE,
//DEBUG,
//INFO,
//WARN,
//ERROR,
//FATAL

var log4js = require('log4js');

log4js.configure({
    appenders: [
        {
            type: 'console',
            category: "console"
        }, //控制台输出
        {
            type: "dateFile",
            filename: 'logs/BTC.log',
            pattern: "-yyyy-MM-dd",
            alwaysIncludePattern: true,
            category: "1000"
        },
        {
            type: "dateFile",
            filename: 'logs/LTC.log',
            pattern: "-yyyy-MM-dd",
            alwaysIncludePattern: true,
            category: "1010"
        },
        {
            type: "dateFile",
            filename: 'logs/DOGE.log',
            pattern: "-yyyy-MM-dd",
            alwaysIncludePattern: true,
            category: "1100"
        },
        {
            type: "dateFile",
            filename: 'logs/DRK.log',
            pattern: "-yyyy-MM-dd",
            alwaysIncludePattern: true,
            category: "1300"
        },
        {
            type: "dateFile",
            filename: 'logs/BC.log',
            pattern: "-yyyy-MM-dd",
            alwaysIncludePattern: true,
            category: "1400"
        },
        {
            type: "dateFile",
            filename: 'logs/VRC.log',
            pattern: "-yyyy-MM-dd",
            alwaysIncludePattern: true,
            category: "1500"
        },
        {
            type: "dateFile",
            filename: 'logs/ZET.log',
            pattern: "-yyyy-MM-dd",
            alwaysIncludePattern: true,
            category: "1600"
        },
        {
            type: "dateFile",
            filename: 'logs/BTSX.log',
            pattern: "-yyyy-MM-dd",
            alwaysIncludePattern: true,
            category: "2100"
        },
        {
            type: "dateFile",
            filename: 'logs/XRP.log',
            pattern: "-yyyy-MM-dd",
            alwaysIncludePattern: true,
            category: "3100"
        },
        {
            type: "dateFile",
            filename: 'logs/XRP-RMB.log',
            pattern: "-yyyy-MM-dd",
            alwaysIncludePattern: true,
            category: "3200"
        },
    ],
    replaceConsole: true, //替换console.log
    levels:{
        1000: 'INFO',
        1010: 'INFO',
        1100: 'INFO',
        1300: 'INFO',
        1400: 'INFO',
        1500: 'INFO',
        1600: 'INFO',
        2100: 'INFO',
        3100: 'INFO',
        3200: 'INFO',
    }
});

exports.logger = function(currency){
    var dateFileLog = log4js.getLogger(currency);
    return dateFileLog;
}

