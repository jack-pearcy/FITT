package com.fitt.shared.account;

// One per bounded context that stores rows for a user. No table has a foreign key
// to users, so nothing is removed by the database when an account goes; each
// context removes what it owns. Called inside the transaction that deletes the
// account, so a failure in any one of them leaves the account whole.
public interface UserDataEraser {

    void eraseUserData(Long userId);
}
