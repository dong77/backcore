#!/bin/sh

##    This program is free software: you can redistribute it and/or modify
##    it under the terms of the GNU General Public License as published by
##    the Free Software Foundation, either version 3 of the License, or
##    (at your option) any later version. For further details see:
##    <http://www.gnu.org/licenses/>.
##
##    This program is distributed in the hope that it will be useful,
##    but WITHOUT ANY WARRANTY; without even the implied warranty of
##    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
##    GNU General Public License for more details.

# =============
# check_process
# =============
# * written by Silver Salonen, Estonia
# * meant for executing by NRPE (Nagios Remote Plugin Executor), but it's not a must ;)
# * the script has been used only by myself (on FreeBSD, Mandrake and Debian)
# * an example for usage: /usr/local/libexec/nagios/check_process.sh amavisd "Amavis daemon" "/usr/local/etc/rc.d/amavisd.sh start" perl
# * common problem is that NRPE gets status UNKNOWN when trying to restart some program (for an instance ntop). As of version 1.3 it may not be a problem anymore, but I'm not very sure about this.

# changelog:

# version 1.3.2 (15.Aug.2011)
# * use named variables for isRunning()
# * if starting the process starts, don't wait until timeout 5 seconds

# (09.Apr.2009)
# include licence

# version 1.3.1 (20.Feb.2007)
# * bugfix: when trying to restart service, say it without inserting newline character, so that the next message may be seen in NRPE output also.

# version 1.3 (23.Feb.2006)
# * output of start-command is sent to /dev/null, instead the start-command itself is echoed.
#   The problem was that NRPE got status of UNKNOWN when start-command's output was more than one line. Very often the start-command didn't manage to finish and the program wasn't started.
#   Now NRPE gets status UNKNOWN with message saying that script is trying to start the program and the next time NRPE gets status OK as the program should be running that time.

# (01.04.2005)
# * script got tested on Debian

# version 1.2.3 (30.06.2004)
# * replaced: 'ps -' -> 'ps ' (i.e. 'ps -c' -> 'ps c'), because linuxes didn't like the "bogus '-'".
# * script got tested on Mandrake 9.2 - works nicely now :)

# version 1.2.2 (30.04.2004)
# * removed sending mail, as there was no actual need for that.
# * after executing the starting-script, it now sleeps 5 seconds instead of 2.

# version 1.2.1 (16.03.2004)
# * important! bugfix:
# ** isRunning(): added `|sed "s/^[ ]*//"` (strips spaces from the beginning of the lines) to getting running pids of a daemon.

# version 1.2 (12.03.2004)
# * isRunning(): uses "-c" in `ps` in any case - whether there is 2nd argument or not.

# version 1.1.1
# * isRunning(): if the 2nd argument is not passed, it uses "ps -cax", otherwise it uses "ps -ax" for checking whether the process is running or not.
# * known bugs:
# ** as the isRunning() uses "ps -ax" when the 2nd argument is passed, it doesn't get the match, when the command in the `ps` is for an instance "proftpd:" instead of "proftpd".

# version 1.1
# * new function: isRunning() - used to find out, whether the daemon is running or not.
# * finding matching processes is much more accurate - isRunning() searches for the exact match, not only for the process that's command contains $daemon.
# * if the script seems to be not working, use `ps` to find out, how the daemon is executed. for an instance - amavisd seems to be executed via perl, but `ps ax -o command |grep amavis` shows: "amavisd (master) (perl)". so, there is no need to specify, that amavisd is executed via perl.
# * NB! be sure to recheck any files, you've used this script in.
# * code is prettier now ;)

# version 1.0
# plugin return codes:
# 0	OK
# 1	Warning
# 2	Critical
# 3	Unknown

print_usage() {
	echo "Usage: $0 [-h] [--help] <daemon> <service description> <daemon starting command> [<executed-via>]"
	echo ""
	echo "Example: $0 amavisd \"Amavis daemon\" \"/usr/local/etc/rc.d/amavisd.sh start\" perl"
}

print_help() {
	print_usage
	echo ""
	echo "This plugin checks if the <daemon> daemon is running."
	echo "For more details, see inside the script ;)"
	echo ""
	exit 3
}

isRunning () { # usage: isRunning <daemon-name> [<executed-via-command>]
	local daemon="$1"
	if [ "$2" ]; then
		local extra="$2"
	fi
# get list of processes, that's command match with <daemon-name> or if $extra is specified, with $extra
	if [ "$2" ]; then
		all=`ps ax | grep "$daemon" | sed "s/^[ ]*//" | awk -F "[\n ]" '{print $1}'`
		for a in $all; do
			pids="$pids $a"
		done
		for a in $pids; do
			two_of_them=`ps c -o command -p $a | awk -F "[\n ]" '{print $1}'`
			j=0
			for i in $two_of_them; do
				j=$(($j + 1))
				if [ $j -eq 2 ]; then
					second_of_them="$i"
				else
					second_of_them=""
				fi
			done
			processes="$processes $second_of_them"
		done
	else
		processes=`ps cax -o command | grep "$daemon" | awk -F "[\n ]" '{print $1}'`
	fi
	notrunning=1
	for p in $processes; do
		if ( [ "$extra" ] && [ "`basename $p`" = "`basename $extra`" ] ) || ( [ ! "$extra" ] && [ "`basename $p`" = "`basename $daemon`" ] ); then
			notrunning=0
			break
		fi
	done
	return $notrunning
}

daemon=$1
desc=$2
starting=$3
extra=$4

if [ ! "$daemon" ]; then
	print_usage
	exit 3
fi

case "$daemon" in
	--help)
		print_help
		exit 3
		;;
	-h)
		print_help
		exit 3
		;;
	*)
		if [ ! "$desc" ]; then # if description of process isn't given
			print_usage
			exit 3
		fi
		if [ ! "$starting" ]; then # if command for starting daemon isn't given
			print_usage
			exit 3
		fi
		if ( isRunning $daemon $extra ); then
			echo "$desc running OK"
			exit 0
		else
			echo -n "$desc not running, trying to start the service with command: $starting ... "
			$starting > /dev/null
			for s in 1 2 3 4 5; do
				if ( isRunning $daemon $extra ); then
					echo "OK!"
					exit 1
				fi
				sleep 1
			done
			# else
			echo "FAILED!"
			exit 2
		fi
		;;
esac
