// 서버 API가 돌려주는 자료의 모양. 서버의 record와 이름이 같다.

export type Member = { id: number; email: string; name: string; role: 'buyer' | 'seller' | 'admin'; adultVerifiedAt: string | null };

export type Seller = {
  id: number; memberId: number; shopName: string; bizNo: string; intro: string; status: 'pending' | 'approved' | 'rejected';
  bankName: string; bankAccount: string; bankHolder: string; payLink: string;
  licenseType: string; licenseNo: string; licenseVerified: boolean;
  baseShipFee: number; freeShipMin: number | null; freeShipStart: string | null; freeShipEnd: string | null;
  commissionRateBp: number | null;
  /** 전자상거래법상 판매자 정보(docs/adr/0002 F). 서버가 아직 보내지 않을 수 있어 없을 수도 있다고 본다. */
  ceoName?: string; bizAddress?: string; mailOrderNo?: string; returnAddress?: string; csPhone?: string;
};

export type Me = { user: Member | null; seller: Seller | null; csrfToken: string; cartCount: number; unreadCount: number };

/** 목록 한 쪽. page 는 1부터 */
export type Paged<T> = { items: T[]; page: number; size: number; total: number };

export type Category = { id: number; parentId: number | null; name: string; alcohol: boolean; commissionRateBp: number | null; sortOrder: number; active: boolean };

export type Product = {
  id: number; sellerId: number; categoryId: number; name: string; description: string; productType: 'physical' | 'ticket';
  price: number; salePrice: number | null; saleStart: string | null; saleEnd: string | null; currentPrice: number;
  stock: number; status: ProductStatus; categoryName: string; topCategoryName: string; alcohol: boolean;
  abv: number | null; volumeMl: number | null; origin: string; mainImage: string | null;
  shopName: string; sellerStatus: string; sellerIntro: string; baseShipFee: number; freeShipMin: number | null;
  /** 리뷰 수(보이는 것만)와 평균 별점(소수 한 자리, 없으면 null). 서버가 아직 보내지 않을 수 있다(docs/adr/0003 A). */
  reviewCount?: number; ratingAvg?: number | null;
};

export type ProductStatus = 'draft' | 'on_sale' | 'paused' | 'ended' | 'blocked';
export type Media = { kind: 'image' | 'video' | 'youtube'; url: string };
/** 상품 상세 아래 "판매자 정보" 표. 계좌 정보는 들어 있지 않다. */
export type SellerInfo = { shopName: string; ceoName: string; bizNo: string; bizAddress: string; mailOrderNo: string; csPhone: string; returnAddress: string };
export type ProductDetail = { product: Product; descriptionDoc: string | null; media: Media[]; sellerInfo?: SellerInfo | null };
export type Notification = { id: number; title: string; body: string; link: string; readAt: string | null; createdAt: string };

export type CartLine = {
  productId: number; name: string; productType: string; categoryName: string; alcohol: boolean;
  unitPrice: number; qty: number; lineTotal: number; stock: number; orderable: boolean;
};
export type CartGroup = { sellerId: number; shopName: string; lines: CartLine[]; itemsTotal: number; hasPhysical: boolean; shipFee: number; total: number };
export type Cart = { groups: CartGroup[]; total: number; hasAlcohol: boolean };

export type SubStatus = 'awaiting_deposit' | 'paid' | 'shipped' | 'delivered' | 'completed' | 'cancelled' | 'refunded';

export type Order = { id: number; orderNo: string; buyerId: number; total: number; receiver: string; phone: string; address: string; cashReceiptNo: string; createdAt: string };
export type SubRow = {
  id: number; orderId: number; sellerId: number; itemsTotal: number; shipFee: number; total: number; hasPhysical: boolean;
  status: SubStatus; depositDueAt: string; depositorName: string | null; depositReportedAt: string | null;
  depositConfirmedAt: string | null; cashReceiptApproval: string | null; carrier: string | null; trackingNo: string | null;
  shippedAt: string | null; deliveredAt: string | null; completedAt: string | null; closedAt: string | null; closeReason: string | null;
  shopName: string; bizNo: string; bankName: string; bankAccount: string; bankHolder: string; payLink: string; sellerMemberId: number;
  /** 쿠폰 할인. 서버가 아직 보내지 않는 경우에 대비해 없을 수도 있다고 본다(?? 0 으로 읽는다). */
  itemDiscount?: number; shipDiscount?: number; couponName?: string | null; couponIssuer?: CouponIssuer | null;
};
export type Item = { id: number; productId: number; name: string; productType: string; unitPrice: number; qty: number; commissionRateBp: number; couponDiscount?: number };
export type Tracking = { provider: string; carrierName: string; link: string | null; steps: { label: string; at: string }[]; delivered: boolean };
/** claim: 이 판매자별 주문의 가장 최근 취소·반품·교환 신청(없으면 null). 서버가 아직 보내지 않으면 undefined. */
export type Sub = { sub: SubRow; items: Item[]; tracking: Tracking | null; claim?: Claim | null };
export type OrderDetail = { order: Order; subs: Sub[] };
export type SellerOrder = { order: Order; sub: Sub };

export type Invoice = { id: number; sellerId: number; shopName: string; periodYm: string; salesTotal: number; amount: number; status: 'issued' | 'paid'; issuedAt: string; paidAt: string | null;
  /** 플랫폼 쿠폰으로 깎아 준 금액(수수료에서 뺀다)과 실제로 낼 금액. net 이 음수면 플랫폼이 판매자에게 보낸다. */
  platformCouponCredit?: number; net?: number;
};
export type Carrier = { code: string; name: string };
export type PostSummary = { id: number; title: string; authorName: string; createdAt: string };
export type Post = PostSummary & { body: string };

// ---- 쿠폰 (docs/adr/0001-coupons.md) ----
export type CouponIssuer = 'platform' | 'seller';
export type DiscountType = 'amount' | 'percent' | 'free_shipping';
export type IssueType = 'signup' | 'download' | 'code' | 'grant';
export type CouponStatus = 'active' | 'paused' | 'ended';
export type Coupon = {
  id: number; issuer: CouponIssuer; sellerId: number | null; shopName: string | null; name: string;
  discountType: DiscountType; discountValue: number; maxDiscount: number | null; minOrder: number;
  categoryId: number | null; categoryName: string | null; excludeAlcohol: boolean;
  /** 만든 사람(판매자·운영자)에게만 보인다. 구매자 응답에서는 항상 null */
  issueType: IssueType; code: string | null;
  totalLimit: number | null; issuedCount: number; validFrom: string; validTo: string; validDays: number | null;
  status: CouponStatus; createdAt: string;
};
export type MemberCoupon = { id: number; coupon: Coupon; status: 'issued' | 'used'; issuedAt: string; expiresAt: string; usedAt: string | null; expired: boolean };
/** GET /coupons 한 줄 */
export type CouponOffer = { coupon: Coupon; owned: boolean; soldOut: boolean };
/** GET /checkout/coupons: 가게별로 지금 쓸 수 있는 내 쿠폰과 할인액 */
export type CheckoutCouponOption = { memberCouponId: number; name: string; issuer: CouponIssuer; discountType: DiscountType; discount: number; shipDiscount: number };
export type CheckoutCouponGroup = { sellerId: number; shopName: string; options: CheckoutCouponOption[] };
/** POST /seller/coupons, POST /admin/coupons 본문. excludeAlcohol 은 운영자만 보낸다. */
export type CouponInput = {
  name: string; discountType: DiscountType; discountValue: number; maxDiscount: number | null; minOrder: number;
  categoryId: number | null; issueType: IssueType; code: string | null; totalLimit: number | null;
  validFrom: string; validTo: string; validDays: number | null; excludeAlcohol?: boolean;
};

// ---- 취소·반품·교환, 찜, 배송지 (docs/adr/0002-claims-wishlist-address.md) ----
export type ClaimType = 'cancel' | 'return' | 'exchange';
export type ClaimStatus = 'requested' | 'approved' | 'rejected' | 'done' | 'withdrawn';
export type ReasonCode = 'changed_mind' | 'defect' | 'wrong_item' | 'late' | 'other';
export type Claim = {
  /** 승인된 반품·교환일 때만: 물건을 돌려보낼 곳 */
  returnAddress?: string | null;
  id: number; subOrderId: number; orderId: number; orderNo: string; shopName: string; buyerName: string;
  claimType: ClaimType; reasonCode: ReasonCode; reasonText: string;
  status: ClaimStatus; sellerNote: string | null; carrier: string | null; trackingNo: string | null;
  /** "상품A × 2, 상품B × 1" 한 줄 요약 */
  items: string;
  /** 판매자별 주문 합계 */
  total: number;
  requestedAt: string; answeredAt: string | null; doneAt: string | null;
};
export type Address = { id: number; label: string; receiver: string; phone: string; address: string; isDefault: boolean };
/** POST·PUT /my/addresses 본문 */
export type AddressInput = Omit<Address, 'id'>;
export type ReorderResult = { added: number; skipped: string[] };
/** GET /seller 의 todo: 가게에서 지금 처리할 일 */
export type SellerTodo = { claimsRequested: number; returnsApproved: number; soldOut: number; shipLate: number; inquiriesUnanswered?: number; reviewsUnanswered?: number };

// ---- 리뷰, 문의, 공지, 가게 화면, 판매 통계 (docs/adr/0003-reviews-inquiries-notices-shop-stats.md) ----
export type ReviewStatus = 'visible' | 'hidden' | 'deleted';
export type Review = {
  id: number; productId: number; productName: string; rating: number; body: string; images: string[];
  authorName: string; mine: boolean; status: ReviewStatus; sellerReply: string | null; repliedAt: string | null; createdAt: string;
};
/** 아직 리뷰를 쓰지 않은 구매 확정 상품 한 줄 */
export type Writable = { orderItemId: number; orderId: number; productId: number; productName: string; shopName: string; qty: number; completedAt: string };
/** GET /products/{id}/reviews. counts 는 별 1개~5개 순서 */
export type ReviewSummary = { ratingAvg: number | null; reviewCount: number; counts: number[]; page: Paged<Review> };

export type InquiryKind = 'product' | 'order' | 'admin';
export type Inquiry = {
  id: number; target: 'seller' | 'admin'; kind: InquiryKind; productId: number | null; productName: string | null;
  subOrderId: number | null; orderId: number | null; orderNo: string | null; shopName: string | null;
  /** 남의 비밀글이면 title 은 "비밀글입니다", body·answer 는 null */
  title: string; body: string | null; secret: boolean; authorName: string; mine: boolean;
  status: 'open' | 'answered'; answer: string | null; answeredAt: string | null; createdAt: string;
};
/** POST /inquiries 본문 */
export type InquiryInput = { target: 'seller' | 'admin'; productId: number | null; subOrderId: number | null; title: string; body: string; secret: boolean };

export type NoticeAudience = 'all' | 'seller';
export type Notice = { id: number; audience: NoticeAudience; title: string; body: string; pinned: boolean; createdAt: string };

export type Shop = {
  id: number; shopName: string; intro: string; ceoName: string; bizNo: string; mailOrderNo: string; csPhone: string;
  productCount: number; reviewCount: number; ratingAvg: number | null;
};
export type ShopPage = { shop: Shop; products: Paged<Product> };

export type SellerStats = {
  days: number; from: string; to: string;
  totals: { orders: number; sales: number; items: number };
  daily: { date: string; orders: number; sales: number }[];
  topProducts: { productId: number; name: string; qty: number; sales: number }[];
};
