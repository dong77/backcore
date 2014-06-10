ps -ef | grep "index_prod.js" | grep -v "grep" | awk '{print $2}' | xargs kill
