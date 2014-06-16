cd ~/coinport/backcore/bitway/
releaseBranch=`git branch -a | grep $1`
if [ -z "$releaseBranch" ];then
  git checkout -b $1 remotes/origin/$1
else
  git checkout $1
fi
./stop.sh
nohup ./run-prod.sh &
