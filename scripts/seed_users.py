"""Create, seed, and verify the MySQL users table."""

import os
import sys

import mysql.connector
from mysql.connector import Error


DB_CONFIG = {
    "host": os.getenv("MYSQL_HOST", "127.0.0.1"),
    "port": int(os.getenv("MYSQL_PORT", "3306")),
    "database": os.getenv("MYSQL_DATABASE", "demo"),
    "user": os.getenv("MYSQL_USER", "demo_user"),
    "password": os.getenv("MYSQL_PASSWORD", "demo_password"),
}

USERS = [
    ("Alice Johnson", 28, "alice@example.com", "New York"),
    ("Bob Smith", 34, "bob@example.com", "London"),
    ("Carol Williams", 25, "carol@example.com", "Toronto"),
    ("David Brown", 41, "david@example.com", "Sydney"),
    ("Eva Davis", 31, "eva@example.com", "Berlin"),
]


def main() -> int:
    connection = None

    try:
        connection = mysql.connector.connect(**DB_CONFIG)
        cursor = connection.cursor()

        cursor.execute(
            """
            CREATE TABLE IF NOT EXISTS users (
                id INT AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                age TINYINT UNSIGNED NOT NULL,
                email VARCHAR(255) NOT NULL UNIQUE,
                city VARCHAR(100) NOT NULL
            )
            """
        )

        cursor.executemany(
            """
            INSERT INTO users (name, age, email, city)
            VALUES (%s, %s, %s, %s)
            ON DUPLICATE KEY UPDATE
                name = VALUES(name),
                age = VALUES(age),
                city = VALUES(city)
            """,
            USERS,
        )
        connection.commit()

        cursor.execute("SELECT id, name, age, email, city FROM users ORDER BY id")
        rows = cursor.fetchall()

        print(f"Connected to MySQL database '{DB_CONFIG['database']}'.")
        print(f"Verified users table: {len(rows)} row(s) found.\n")
        print("id | name            | age | email                | city")
        print("---+-----------------+-----+----------------------+----------")
        for user_id, name, age, email, city in rows:
            print(f"{user_id:<2} | {name:<15} | {age:<3} | {email:<20} | {city}")

        cursor.close()
        return 0

    except Error as error:
        print(f"Database error: {error}", file=sys.stderr)
        print("Is the MySQL container running? Try: docker compose up -d", file=sys.stderr)
        return 1

    finally:
        if connection and connection.is_connected():
            connection.close()


if __name__ == "__main__":
    raise SystemExit(main())
