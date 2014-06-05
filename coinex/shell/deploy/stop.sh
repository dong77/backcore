ps -ef | grep "start-coinex-backend.sh" | grep -v "grep" | awk '{print $2}' | xargs kill
ps -ef | grep "com.coinport.coinex.CoinexApp" | grep -v "grep" | awk '{print $2}' | xargs kill
