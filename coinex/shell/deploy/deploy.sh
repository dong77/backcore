ssh -i ~/work/xiaolu.pem ubuntu@54.238.180.101 "/home/ubuntu/coinport/backcore/coinex/shell/deploy/generateTar.sh $1"
ssh -i ~/work/xiaolu.pem ubuntu@54.238.180.101 'cd /home/ubuntu/coinport/backcore/coinex/coinex-backend/target/scala-2.10 && ../../../shell/deploy/start-coinex-backend.sh'
