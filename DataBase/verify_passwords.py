import bcrypt

# username -> password pairs from the SQL file
users = {
    '20260325': '20260325',
    '123': '123',
    '321': '321',
    '1': '1',
    'admin': 'admin',
    '123456': '123456',
    'xdx': 'xdx',
    'btwo': 'btwo',
    '666': '666',
    'wusuowei': 'wusuowei',
    '1788': '1788',
    '20050329': '20050329',
}

# Stored hashes from the SQL file
stored_hashes = {
    '20260325': '$2a$10$Sf3oroWr6NjeKvjPwN9RUuIUFi0xzw8edTXLp5ZfmkeYm88XhVR2u',
    '123': '$2a$10$YFSHRwo4OpsuFq0hksNo7OYFnezGtXTQ6vkGy168T0Xc8Nijc6xBi',
    '321': '$2a$10$aTXijTAhE4pgi6LLHXBPnebgG9LeOSZlh0BZ9sA6olucR9UkEzB72',
    '1': '$2a$10$fbD2b0YtvNWeWxwIUfrx2.4wIwYTn3HVMSDTSN2vpdWsDMItKoRgG',
    'admin': '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    '123456': '$2a$10$oX7Es2errEvO/OdQOZCYGe27bpDrBrkQlFkMCaAuLpCeOm3v8yWee',
    'xdx': '$2a$10$RVd.0z388KXoWptD20XYhe7PQowcW5Zt3d40mSfZUMf5.6B6SbwEC',
    'btwo': '$2a$10$WCXI5OuBbOR7N6N1fcjDVu5zSdfUaGaOk8Ybd7SKQ1Ec7KKhOz8Xm',
    '666': '$2a$10$F/6IM3myBATlLi.jW2VxduYP4dF3WUWhkmEifwJDEcBYMYaxU1d9i',
    'wusuowei': '$2a$10$2gY8VFJ8uFBZdYJZWEa8Ven8h2c0Vzzv3oZzi7ZOi4nnkK54f5ePG',
    '1788': '$2a$10$lI4yPyDexWkhN5XXlgNNm.kGtMtiy17rlR2WGtDfRyRXq0rUeNBUS',
    '20050329': '$2a$10$1UyVhEDXz/ivDLeRU/Q16evJYh2aBOzJEPIW2cYxbDaAToTOjCEfa',
}

print("=== Verifying existing hashes ===")
for username, password in users.items():
    stored_hash = stored_hashes[username]
    try:
        is_valid = bcrypt.checkpw(password.encode('utf-8'), stored_hash.encode('utf-8'))
        print(f"User '{username}' (pwd='{password}'): hash_len={len(stored_hash)}, valid={is_valid}")
    except Exception as e:
        print(f"User '{username}': ERROR - {e}")

print("\n=== Generating new hashes ===")
for username, password in users.items():
    new_hash = bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')
    print(f"User '{username}': {new_hash} (len={len(new_hash)})")
