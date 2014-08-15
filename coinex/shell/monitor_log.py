#!/usr/bin/python
# -*- coding: utf-8 -*-
#
# Copyright 2014 Coinport.com. All Rights Reserved.

from __future__ import with_statement
from optparse import OptionParser
import sys
import os
import re

import smtplib
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText

__author__ = 'kongliang@coinport.com (Zhong Kongliang)'

SEVERITY_OK = 0
SEVERITY_CRITICAL = 1
SEVERITY_UNKNOWN = 2
SEVERITY_WARNING = 3

FETCH_LINE_NUM = 10000
MAX_LINE_LEN = 512
detectStrings = ['Exception', 'ERROR', 'WARN']

date_reg_exp = re.compile('\d{4}[-/]\d{2}[-/]\d{2}')
line_num_cache_file = "line_num_cache"
CACHE_TEXT_SIZE = 32

mail_from = "cp_admin_alarm@163.com"
mail_from_pwd = "coinport1234567"
mail_to = ['jaice_229@163.com','xiaolu@coinport.com', 'c@coinport.com', 'weichao@coinport.com','chunming@coinport.com','chenxi@coinport.com', 'yangli@coinport.com', 'kongliang@coinport.com']
#mail_to = "xiaolu@coinport.com"
SERVER_NAME = "exchange frontend "

def write_line_num(num, last_line):
    f = open(line_num_cache_file, "w")
    f.write(str(num) + "\n")
    f.write(last_line + "\n")
    f.close

def seek_to_prior_position(logf):
    try:
        with open(line_num_cache_file, "r") as f:
            prior_pos = int(f.readline().rstrip())
            cached_str = f.readline()[:(CACHE_TEXT_SIZE - 2)]
            logf.seek(prior_pos - CACHE_TEXT_SIZE, 0)
            curr_str = logf.read(CACHE_TEXT_SIZE)
            if curr_str.startswith(cached_str):
                return
            else:
                logf.seek(0, 0)
    except IOError:
        logf.seek(0, 0)

def read_ranged_lines(f):
    seek_to_prior_position(f)
    lines = []
    for i, line in enumerate(f):
        lines.append(line.rstrip())
        if i == FETCH_LINE_NUM - 1:
            break;

    if len(lines) > 0:
        read_pos = f.tell()
        f.seek(-CACHE_TEXT_SIZE, 1)
        cache_str = f.read(CACHE_TEXT_SIZE)
        write_line_num(read_pos, cache_str)
    return lines

def insert_message_dict(dict, key, value):
    if ("WARN" in value) or ("ERROR" in value) or ("FATAL" in value):
        value = value[:MAX_LINE_LEN]
        if not dict.has_key(key):
            dict[key] = [value]
        else:
            dict[key].append(value)

def send_mail(content):
    msg = MIMEMultipart('alternative')
    msg['Subject'] = SERVER_NAME + ""
    msg['From'] = mail_from
    msg['To'] = ", ".join(mail_to)
    msg.attach(MIMEText(content, 'plain'))
    s = smtplib.SMTP('smtp.163.com')
    s.login(mail_from, mail_from_pwd)
    try:
        s.sendmail(mail_from, mail_to, msg.as_string())
        print 'send mail succeeded.'
    finally:
        s.close()

    

def main():
    parser = OptionParser()
    parser.add_option("-f", "--file", dest="logfile", help="log file to parse.")
    (options, args) = parser.parse_args()
    if options.logfile is None:
        parser.print_help()
        exit(-1)

    except_message_dict = {}
    f = open(options.logfile, "r")
    while True:
        lines = read_ranged_lines(f)
        for line in lines:
            for ds in detectStrings:
                if re.match(date_reg_exp, line) and ds in line:
                    insert_message_dict(except_message_dict, ds, line)
                    break
        if len(lines) < FETCH_LINE_NUM:
            break
    f.close()

    error_code = SEVERITY_OK
    if except_message_dict.has_key(detectStrings[0]) or except_message_dict.has_key(detectStrings[1]):
        error_code = SEVERITY_CRITICAL
    elif except_message_dict.has_key(detectStrings[2]):
        error_code = SEVERITY_WARNING
    else:
        error_code = SEVERITY_OK

    if error_code == SEVERITY_OK or error_code == SEVERITY_WARNING :
        print '\033[32mSuccess!\033[0m'
    else:
        print '\033[31mFail!\033[0m, level=' + str(error_code)
        for key in except_message_dict.keys():
            mail_content =  "\n" + key + ":" + "\n"
            for msg in except_message_dict[key]:
                mail_content = mail_content + msg + "\n"
                
        if len(mail_content) > 5000:
            mail_content = mail_content[:5000] + "..."
        print mail_content
        #command = 'mail -s "backend_restart" ' + mail_to + ' ' + mail_content
        #os.system(command)
        send_mail(mail_content)
    exit(error_code)

if __name__ == '__main__':
    main()
