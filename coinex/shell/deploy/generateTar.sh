cd ~/coinport/backcore/coinex
git fetch
git checkout -b $1 remotes/origin/$1
./activator clean
./activator assembly
cp ~/coinport/backcore/coinex/coinex-backend/target/scala-2.10/coinex-backend-assembly-* ~/coinport/coinex-backend/
