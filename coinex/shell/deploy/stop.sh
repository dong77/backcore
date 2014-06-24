ps -ef | grep "com.coinport.coinex.CoinexApp 25551" | grep -v "grep" | awk '{print $2}' | xargs kill
