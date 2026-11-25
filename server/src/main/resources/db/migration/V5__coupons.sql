-- 쿠폰(docs/adr/0001-coupons.md). 발행자(issuer)가 곧 할인 금액을 부담하는 쪽이다.

CREATE TABLE coupon (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT      NOT NULL,
    issuer          VARCHAR(10) NOT NULL,
    seller_id       BIGINT,
    name            VARCHAR(60) NOT NULL,
    discount_type   VARCHAR(20) NOT NULL,
    discount_value  INT         NOT NULL,
    max_discount    BIGINT,
    min_order       BIGINT      NOT NULL DEFAULT 0,
    category_id     BIGINT,
    exclude_alcohol BOOLEAN     NOT NULL DEFAULT TRUE,
    issue_type      VARCHAR(10) NOT NULL,
    code            VARCHAR(40),
    total_limit     INT,
    issued_count    INT         NOT NULL DEFAULT 0,
    valid_from      DATE        NOT NULL,
    valid_to        DATE        NOT NULL,
    valid_days      INT,
    status          VARCHAR(10) NOT NULL DEFAULT 'active',
    created_at      DATETIME(6) NOT NULL,
    -- 코드는 대문자로 저장한다. 코드가 없는 쿠폰(NULL)은 여러 개여도 된다.
    CONSTRAINT uq_coupon_code UNIQUE (tenant_id, code),
    CONSTRAINT fk_coupon_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_coupon_seller FOREIGN KEY (seller_id) REFERENCES seller (id),
    CONSTRAINT fk_coupon_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT ck_coupon_issuer CHECK (issuer IN ('platform', 'seller')),
    CONSTRAINT ck_coupon_discount_type CHECK (discount_type IN ('amount', 'percent', 'free_shipping')),
    CONSTRAINT ck_coupon_issue_type CHECK (issue_type IN ('signup', 'download', 'code', 'grant')),
    CONSTRAINT ck_coupon_status CHECK (status IN ('active', 'paused', 'ended')),
    CONSTRAINT ck_coupon_issued CHECK (issued_count >= 0)
);
CREATE INDEX idx_coupon_list ON coupon (tenant_id, issue_type, status);
CREATE INDEX idx_coupon_seller ON coupon (seller_id);

CREATE TABLE member_coupon (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id    BIGINT      NOT NULL,
    coupon_id    BIGINT      NOT NULL,
    member_id    BIGINT      NOT NULL,
    status       VARCHAR(10) NOT NULL DEFAULT 'issued',
    issued_at    DATETIME(6) NOT NULL,
    expires_at   DATETIME(6) NOT NULL,
    used_at      DATETIME(6),
    sub_order_id BIGINT,
    -- 한 회원은 같은 쿠폰을 한 장만 가진다(두 번 받기 요청이 동시에 와도 한 장).
    CONSTRAINT uq_member_coupon UNIQUE (coupon_id, member_id),
    CONSTRAINT fk_mc_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_mc_coupon FOREIGN KEY (coupon_id) REFERENCES coupon (id),
    CONSTRAINT fk_mc_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_mc_sub FOREIGN KEY (sub_order_id) REFERENCES sub_order (id),
    CONSTRAINT ck_mc_status CHECK (status IN ('issued', 'used'))
);
CREATE INDEX idx_mc_member ON member_coupon (member_id, status);
CREATE INDEX idx_mc_sub ON member_coupon (sub_order_id);

-- 판매자별 주문에 쓴 쿠폰. 쿠폰 이름·발행자는 주문 시점 값으로 복사해 둔다.
-- member_coupon_id 에는 외래 키를 두지 않는다: member_coupon.sub_order_id 와 서로 가리키면 지우는 순서가 꼬인다.
ALTER TABLE sub_order ADD COLUMN item_discount BIGINT NOT NULL DEFAULT 0;
ALTER TABLE sub_order ADD COLUMN ship_discount BIGINT NOT NULL DEFAULT 0;
ALTER TABLE sub_order ADD COLUMN member_coupon_id BIGINT;
ALTER TABLE sub_order ADD COLUMN coupon_issuer VARCHAR(10);
ALTER TABLE sub_order ADD COLUMN coupon_name VARCHAR(60);

ALTER TABLE order_item ADD COLUMN coupon_discount BIGINT NOT NULL DEFAULT 0;

ALTER TABLE commission_invoice ADD COLUMN platform_coupon_credit BIGINT NOT NULL DEFAULT 0;
