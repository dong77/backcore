ssh -i ~/work/xiaolu.pem ubuntu@54.238.180.101 "/var/coinport/code/backcore/coinex/shell/deploy/generateTar.sh $1"
ssh -i ~/work/xiaolu.pem ubuntu@54.238.180.101 'cd /var/coinport/backend && /var/coinport/code/backcore/coinex/shell/deploy/start-coinex-backend.sh'
