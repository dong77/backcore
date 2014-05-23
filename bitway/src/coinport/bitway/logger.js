/**
 *Copyright 2014 Coinport Inc. All Rights Reserved.
 *Author: YangLi--ylautumn84@gmail.com
 *Filename: logger.js
 *Description: 
 */

//'BTC' : 1000,
//'LTC' : 1010,
//'PTS' : 1200,
//'DOG' : 1100 

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
            filename: 'logs/DOG.log',  
            pattern: "-yyyy-MM-dd",  
            alwaysIncludePattern: true,  
            category: "1100"  
        }  
    ],  
    replaceConsole: true, //替换console.log  
    levels:{  
        1000: 'INFO',
        1010: 'INFO',
        1100: 'DEBUG',
    }  
});  

exports.logger = function(currency){ 
    var dateFileLog = log4js.getLogger(currency);  
    return dateFileLog;
}  
  
