cd ~/coinport/backcore/coinex
git fetch
git checkout -b $1 remotes/origin/$1
./activator clean
./activator assembly
