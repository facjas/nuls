/*
 * MIT License
 *
 * Copyright (c) 2017-2018 nuls.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package io.nuls.account.ledger.base.manager;

import io.nuls.account.ledger.base.util.CoinComparator;
import io.nuls.account.ledger.constant.AccountLedgerErrorCode;
import io.nuls.account.ledger.storage.service.LocalUtxoStorageService;
import io.nuls.account.model.Account;
import io.nuls.account.model.Address;
import io.nuls.account.model.Balance;
import io.nuls.account.service.AccountService;
import io.nuls.core.tools.crypto.Base58;
import io.nuls.core.tools.log.Log;
import io.nuls.db.model.Entry;
import io.nuls.kernel.exception.NulsException;
import io.nuls.kernel.lite.annotation.Autowired;
import io.nuls.kernel.lite.annotation.Component;
import io.nuls.kernel.model.Coin;
import io.nuls.kernel.model.Na;
import io.nuls.kernel.model.Result;
import io.nuls.kernel.utils.AddressTool;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 管理与缓存本地账户的余额
 */

@Component
public class BalanceManager {

    @Autowired
    private LocalUtxoStorageService localUtxoStorageService;
    @Autowired
    private AccountService accountService;

    private Map<String, Balance> balanceMap = new HashMap<>();

    Lock lock = new ReentrantLock();

    /**
     * 初始化缓存本地所有账户的余额信息
     */
    public void initAccountBalance() {
        balanceMap.clear();

        List<Account> accounts = accountService.getAccountList().getData();
        if (accounts == null) {
            return;
        }

        for (Account account : accounts) {
            try {
                calBalanceByAddress(account.getAddress().getBase58Bytes());
            } catch (NulsException e) {
                Log.info("getbalance of address[" + account.getAddress().getBase58() + "] error");
            }
        }
    }

    /**
     * 获取账户余额
     *
     * @param address
     * @return
     */
    public Result<Balance> getBalance(Address address) {
        return getBalance(address.getBase58Bytes());
    }

    /**
     * 获取账户余额
     *
     * @param address
     * @return
     */
    public Result<Balance> getBalance(byte[] address) {
        lock.lock();
        try {
            if (address == null || address.length != AddressTool.HASH_LENGTH) {
                return Result.getFailed(AccountLedgerErrorCode.PARAMETER_ERROR);
            }
            Result<Account> accountResult = accountService.getAccount(address);
            if (accountResult.isFailed()) {
                return Result.getFailed(accountResult.getErrorCode());
            }

            String addressKey = new String(address);
            Balance balance = balanceMap.get(addressKey);
            if (balance == null) {
                try {
                    balance = calBalanceByAddress(address);
                } catch (NulsException e) {
                    Log.info("getbalance of address[" + Base58.encode(address) + "] error");
                }
            }
            return Result.getSuccess().setData(balance);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 刷新余额，其实就是删除缓存，下次获取时再重新计算
     *
     * @param address
     */
    public void refreshBalance(byte[] address) {
        lock.lock();
        try {
            if (address != null) {
                balanceMap.remove(new String(address));
            }
        } finally {
            lock.unlock();
        }
    }

    public void refreshBalance() {
        lock.lock();
        try {
            balanceMap.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 计算账户的余额，这个方法应该和获取余额方法互斥，避免并发导致数据不准确
     *
     * @param address
     * @return
     * @throws NulsException
     */
    public Balance calBalanceByAddress(byte[] address) throws NulsException {
        lock.lock();
        try {
            if (accountService.getAccount(address).isFailed()) {
                return null;
            }
            List<Coin> coinList = getCoinListByAddress(address);
            Collections.sort(coinList, CoinComparator.getInstance());

            Na usable = Na.ZERO;
            Na locked = Na.ZERO;
            for (Coin coin : coinList) {
                if (coin.usable()) {
                    usable = usable.add(coin.getNa());
                } else {
                    locked = locked.add(coin.getNa());
                }
            }

            Balance balance = new Balance();
            balance.setUsable(usable);
            balance.setLocked(locked);
            balance.setBalance(usable.add(locked));

            balanceMap.put(new String(address), balance);
            return balance;
        } finally {
            lock.unlock();
        }
    }

    public List<Coin> getCoinListByAddress(byte[] address) {
        List<Coin> coinList = new ArrayList<>();
        List<Entry<byte[], byte[]>> rawList = localUtxoStorageService.loadAllCoinList();
        for (Entry<byte[], byte[]> coinEntry : rawList) {
            Coin coin = new Coin();
            try {
                coin.parse(coinEntry.getValue());
            } catch (NulsException e) {
                Log.info("parse coin form db error");
                continue;
            }
            if (Arrays.equals(coin.getOwner(), address)) {
                coin.setOwner(coinEntry.getKey());
                coinList.add(coin);
            }
        }
        return coinList;
    }
}
