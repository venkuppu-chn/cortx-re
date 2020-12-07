#! /bin/bash
# ./update_hosts server1 server2

sed -i "s/<SRVNODE-1_HOST>/$1/g" inventories/sw_update/hosts
