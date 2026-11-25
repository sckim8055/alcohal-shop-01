// 서버 API가 돌려주는 자료의 모양. 서버의 record와 이름이 같다.

export type Member = { id: number; email: string; name: string; role: 'buyer' | 'seller' | 'admin'; adultVerifiedAt: string | null };

export type Seller = {
  id: number; memberId: number; shopName: string; bizNo: string; intro: string; status: 'pending' | 'approved' | 'rejected';
  bankName: string; bankAccount: string; bankHolder: string; payLink: string;
  licenseType: string; licenseNo: string; licenseVerified: boolean;
  baseShipFee: number; freeShipMin: number | null; freeShipStart: string | null; freeShipEnd: string | null;
  commissionRateBp: number | null;
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
};

export type ProductStatus = 'draft' | 'on_sale' | 'paused' | 'ended' | 'blocked';
export type Media = { kind: 'image' | 'video' | 'youtube'; url: string };
export type ProductDetail = { product: Product; descriptionDoc: string | null; media: Media[] };
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
};
export type Item = { id: number; productId: number; name: string; productType: string; unitPrice: number; qty: number; commissionRateBp: number };
export type Tracking = { provider: string; carrierName: string; link: string | null; steps: { label: string; at: string }[]; delivered: boolean };
export type Sub = { sub: SubRow; items: Item[]; tracking: Tracking | null };
export type OrderDetail = { order: Order; subs: Sub[] };
export type SellerOrder = { order: Order; sub: Sub };

export type Invoice = { id: number; sellerId: number; shopName: string; periodYm: string; salesTotal: number; amount: number; status: 'issued' | 'paid'; issuedAt: string; paidAt: string | null };
export type Carrier = { code: string; name: string };
export type PostSummary = { id: number; title: string; authorName: string; createdAt: string };
export type Post = PostSummary & { body: string };
