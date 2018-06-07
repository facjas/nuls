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

package io.nuls.account.service;

import io.nuls.account.constant.AccountConstant;
import io.nuls.account.model.Account;
import io.nuls.account.model.Address;
import io.nuls.cache.CacheMap;
import io.nuls.core.tools.crypto.Base58;

import java.util.List;

/**
 * 账户缓存服务类
 * Account Cache Service
 *
 * @author: Charlie
 * @date: 2018/5/9
 */
public class AccountCacheService {

    private static final AccountCacheService INSTANCE = new AccountCacheService();

    private CacheMap<String, Account> cacheMap;

    private AccountCacheService() {
        this.cacheMap = new CacheMap<>(AccountConstant.ACCOUNT_LIST_CACHE, 32, String.class, Account.class);
    }


    public static AccountCacheService getInstance() {
        return INSTANCE;
    }

    /**
     * 缓存一个账户
     * Cache an account
     *
     * @param account Account to be cached
     */
    public void putAccount(Account account) {
        this.cacheMap.put(account.getAddress().getBase58(), account);
    }

    /**
     * 根据账户地址获取账户详细信息
     * Get accounts based on account address
     *
     * @param address Account to be operated
     * @return
     */
    public Account getAccountByAddress(String address) {
        List<Account> list = this.getAccountList();
        for (Account account : list) {
            if (account.getAddress().toString().equalsIgnoreCase(address)) {
                return account;
            }
        }
        return null;
    }

    /**
     * 验证账户是否存在
     * Verify the existence of the account
     * @param address
     * @return
     */
    public boolean contains(byte[] address) {
        return this.cacheMap.containsKey(Base58.encode(address));
    }

    /**
     * 获取所有账户
     * Get all accounts
     * @return List<Account>
     */
    public List<Account> getAccountList() {
        return this.cacheMap.values();
    }

    public void removeAccount(Address address) {
        this.cacheMap.remove(address.getBase58());
    }

    public void removeAccount(byte[] address) {
        this.cacheMap.remove(Base58.encode(address));
    }


    public void clear() {
        this.cacheMap.clear();
    }

    public void destroy() {
        this.cacheMap.destroy();
    }

    /**
     * 缓存多个账户
     * Cache multiple accounts
     * @param list
     */
    public void putAccountList(List<Account> list) {
        if (null != list) {
            for (Account account : list) {
                this.putAccount(account);
            }
        }
    }
}
