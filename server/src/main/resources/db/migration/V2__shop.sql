-- 술장터 1단계: 회원, 판매자, 상품, 장바구니, 주문, 수수료, 게시판.
-- 금액은 원 단위 정수, 수수료율은 1만분율(100 = 1%), 시각은 UTC로 저장한다.

INSERT INTO category (tenant_id, name, is_alcohol, commission_rate_bp) VALUES (1, '강의·체험', FALSE, NULL);

CREATE TABLE app_setting (
    tenant_id     BIGINT       NOT NULL,
    setting_key   VARCHAR(100) NOT NULL,
    setting_value VARCHAR(500) NOT NULL,
    PRIMARY KEY (tenant_id, setting_key)
);
INSERT INTO app_setting (tenant_id, setting_key, setting_value) VALUES (1, 'default_commission_rate_bp', '300');

CREATE TABLE member (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id         BIGINT       NOT NULL,
    email             VARCHAR(190) NOT NULL,
    password_hash     VARCHAR(200) NOT NULL,
    name              VARCHAR(40)  NOT NULL,
    role              VARCHAR(10)  NOT NULL DEFAULT 'buyer',
    adult_verified_at DATETIME(6),
    created_at        DATETIME(6)  NOT NULL,
    CONSTRAINT uq_member_email UNIQUE (tenant_id, email),
    CONSTRAINT fk_member_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT ck_member_role CHECK (role IN ('buyer', 'seller', 'admin'))
);

CREATE TABLE seller (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id          BIGINT       NOT NULL,
    member_id          BIGINT       NOT NULL,
    shop_name          VARCHAR(40)  NOT NULL,
    biz_no             VARCHAR(20)  NOT NULL,
    intro              VARCHAR(500) NOT NULL DEFAULT '',
    status             VARCHAR(10)  NOT NULL DEFAULT 'pending',
    bank_name          VARCHAR(30)  NOT NULL DEFAULT '',
    bank_account       VARCHAR(40)  NOT NULL DEFAULT '',
    bank_holder        VARCHAR(40)  NOT NULL DEFAULT '',
    pay_link           VARCHAR(300) NOT NULL DEFAULT '',
    license_type       VARCHAR(40)  NOT NULL DEFAULT '',
    license_no         VARCHAR(40)  NOT NULL DEFAULT '',
    license_verified   BOOLEAN      NOT NULL DEFAULT FALSE,
    base_ship_fee      INT          NOT NULL DEFAULT 3500,
    free_ship_min      INT,
    free_ship_start    DATE,
    free_ship_end      DATE,
    commission_rate_bp INT,
    created_at         DATETIME(6)  NOT NULL,
    CONSTRAINT uq_seller_member UNIQUE (member_id),
    CONSTRAINT fk_seller_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_seller_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT ck_seller_status CHECK (status IN ('pending', 'approved', 'rejected'))
);

CREATE TABLE product (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id    BIGINT        NOT NULL,
    seller_id    BIGINT        NOT NULL,
    category_id  BIGINT        NOT NULL,
    name         VARCHAR(80)   NOT NULL,
    description  VARCHAR(2000) NOT NULL DEFAULT '',
    product_type VARCHAR(10)   NOT NULL DEFAULT 'physical',
    price        BIGINT        NOT NULL,
    sale_price   BIGINT,
    sale_start   DATE,
    sale_end     DATE,
    stock        INT           NOT NULL DEFAULT 0,
    status       VARCHAR(10)   NOT NULL DEFAULT 'active',
    created_at   DATETIME(6)   NOT NULL,
    CONSTRAINT fk_product_seller FOREIGN KEY (seller_id) REFERENCES seller (id),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT ck_product_type CHECK (product_type IN ('physical', 'ticket')),
    CONSTRAINT ck_product_status CHECK (status IN ('active', 'hidden')),
    CONSTRAINT ck_product_price CHECK (price >= 0),
    CONSTRAINT ck_product_stock CHECK (stock >= 0)
);
CREATE INDEX idx_product_seller ON product (seller_id);
CREATE INDEX idx_product_list ON product (tenant_id, status, category_id);

CREATE TABLE cart_item (
    member_id  BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    qty        INT    NOT NULL,
    PRIMARY KEY (member_id, product_id),
    CONSTRAINT fk_cart_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_cart_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT ck_cart_qty CHECK (qty > 0)
);

CREATE TABLE shop_order (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL,
    order_no        VARCHAR(30)  NOT NULL,
    buyer_id        BIGINT       NOT NULL,
    total           BIGINT       NOT NULL,
    receiver        VARCHAR(40)  NOT NULL,
    phone           VARCHAR(30)  NOT NULL,
    address         VARCHAR(300) NOT NULL DEFAULT '',
    cash_receipt_no VARCHAR(30)  NOT NULL DEFAULT '',
    created_at      DATETIME(6)  NOT NULL,
    CONSTRAINT uq_order_no UNIQUE (order_no),
    CONSTRAINT fk_order_buyer FOREIGN KEY (buyer_id) REFERENCES member (id)
);
CREATE INDEX idx_order_buyer ON shop_order (buyer_id);

CREATE TABLE sub_order (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id             BIGINT      NOT NULL,
    order_id              BIGINT      NOT NULL,
    seller_id             BIGINT      NOT NULL,
    items_total           BIGINT      NOT NULL,
    ship_fee              BIGINT      NOT NULL,
    total                 BIGINT      NOT NULL,
    has_physical          BOOLEAN     NOT NULL DEFAULT TRUE,
    status                VARCHAR(20) NOT NULL DEFAULT 'awaiting_deposit',
    deposit_due_at        DATETIME(6) NOT NULL,
    depositor_name        VARCHAR(40),
    deposit_reported_at   DATETIME(6),
    deposit_confirmed_at  DATETIME(6),
    cash_receipt_approval VARCHAR(30),
    carrier               VARCHAR(20),
    tracking_no           VARCHAR(40),
    shipped_at            DATETIME(6),
    delivered_at          DATETIME(6),
    completed_at          DATETIME(6),
    closed_at             DATETIME(6),
    close_reason          VARCHAR(100),
    CONSTRAINT fk_sub_order FOREIGN KEY (order_id) REFERENCES shop_order (id),
    CONSTRAINT fk_sub_seller FOREIGN KEY (seller_id) REFERENCES seller (id),
    CONSTRAINT ck_sub_status CHECK (status IN
        ('awaiting_deposit', 'paid', 'shipped', 'delivered', 'completed', 'cancelled', 'refunded'))
);
CREATE INDEX idx_sub_seller ON sub_order (seller_id, status);
CREATE INDEX idx_sub_order ON sub_order (order_id);

CREATE TABLE order_item (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    sub_order_id       BIGINT      NOT NULL,
    product_id         BIGINT      NOT NULL,
    name               VARCHAR(80) NOT NULL,
    product_type       VARCHAR(10) NOT NULL,
    unit_price         BIGINT      NOT NULL,
    qty                INT         NOT NULL,
    commission_rate_bp INT         NOT NULL,
    CONSTRAINT fk_item_sub FOREIGN KEY (sub_order_id) REFERENCES sub_order (id),
    CONSTRAINT fk_item_product FOREIGN KEY (product_id) REFERENCES product (id)
);
CREATE INDEX idx_item_sub ON order_item (sub_order_id);

CREATE TABLE commission_rate_log (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id  BIGINT      NOT NULL,
    scope      VARCHAR(10) NOT NULL,
    target_id  BIGINT,
    rate_bp    INT,
    changed_by BIGINT      NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_ratelog_member FOREIGN KEY (changed_by) REFERENCES member (id)
);

CREATE TABLE commission_invoice (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT      NOT NULL,
    seller_id   BIGINT      NOT NULL,
    period_ym   CHAR(7)     NOT NULL,
    sales_total BIGINT      NOT NULL,
    amount      BIGINT      NOT NULL,
    status      VARCHAR(10) NOT NULL DEFAULT 'issued',
    issued_at   DATETIME(6) NOT NULL,
    paid_at     DATETIME(6),
    CONSTRAINT uq_invoice UNIQUE (seller_id, period_ym),
    CONSTRAINT fk_invoice_seller FOREIGN KEY (seller_id) REFERENCES seller (id),
    CONSTRAINT ck_invoice_status CHECK (status IN ('issued', 'paid'))
);

CREATE TABLE post (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id  BIGINT        NOT NULL,
    member_id  BIGINT        NOT NULL,
    title      VARCHAR(100)  NOT NULL,
    body       VARCHAR(5000) NOT NULL,
    created_at DATETIME(6)   NOT NULL,
    CONSTRAINT fk_post_member FOREIGN KEY (member_id) REFERENCES member (id)
);
