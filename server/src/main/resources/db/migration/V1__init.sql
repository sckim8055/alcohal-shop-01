-- 몰(테넌트)과 카테고리. 수수료율은 1만분율(100 = 1%)로 저장한다.
CREATE TABLE tenant (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL UNIQUE,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE category (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id          BIGINT       NOT NULL,
    name               VARCHAR(100) NOT NULL,
    is_alcohol         BOOLEAN      NOT NULL DEFAULT FALSE,
    commission_rate_bp INT,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_category_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT uq_category_name UNIQUE (tenant_id, name)
);

INSERT INTO tenant (id, code, name) VALUES (1, 'sool', '술장터');

INSERT INTO category (tenant_id, name, is_alcohol, commission_rate_bp) VALUES
    (1, '전통주', TRUE, 100),
    (1, '원재료', FALSE, NULL),
    (1, '양조 기구', FALSE, NULL),
    (1, '술잔·주기', FALSE, NULL);
