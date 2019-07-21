import { environment } from '@src/environments/environment';
import jsonToUrlParams from '@app/helpers/jsonToUrlParams';

const base: string = environment.baseUrl + 'launchpad/account';

const urls: any = {
    accounts: {
        get: (page: number): string => `${base}/accounts?page=${page}`
    },
    account: {
        get: (id: number | string): string => `${base}/account/${id}`,
        addCommit: (data): string => `${base}/account-add-commit?${jsonToUrlParams(data)}`,
        editCommit: (data): string => `${base}/account-edit-commit?${jsonToUrlParams(data)}`,
        passwordEditCommit: (data): string => `${base}/account-password-edit-commit?${jsonToUrlParams(data)}`,
    }
};

export {
    urls
};

// @GetMapping("/accounts")
// public AccountData.AccountsResult accounts(@PageableDefault(size = 5) Pageable pageable) {
//     return accountTopLevelService.getAccounts(pageable);
// }

// @PostMapping("/account-add-commit")
// public OperationStatusRest addFormCommit(@RequestBody Account account) {
//     return accountTopLevelService.addAccount(account);
// }

// @GetMapping(value = "/account/{id}")
// public AccountData.AccountResult getAccount(@PathVariable Long id) {
//     return accountTopLevelService.getAccount(id);
// }

// @PostMapping("/account-edit-commit")
// public OperationStatusRest editFormCommit(Long id, String publicName, boolean enabled) {
//     return accountTopLevelService.editFormCommit(id, publicName, enabled);
// }

// @PostMapping("/account-password-edit-commit")
// public OperationStatusRest passwordEditFormCommit(Long id, String password, String password2) {
//     return accountTopLevelService.passwordEditFormCommit(id, password, password2);
// }