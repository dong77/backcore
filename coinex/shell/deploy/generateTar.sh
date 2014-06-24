cd /var/coinport/code/backcore/coinex
git fetch
git checkout -b $1 origin/$1
git rebase origin/$1
branch=`git branch | grep "*" | awk '{print $2}'`
echo "current branch is "$branch
./activator clean
./activator assembly
cp coinex-backend/target/scala-2.10/coinex-backend-assembly-* /var/coinport/backend/
