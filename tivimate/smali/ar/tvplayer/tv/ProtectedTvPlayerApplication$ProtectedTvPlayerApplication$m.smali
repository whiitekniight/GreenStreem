.class public final Lar/tvplayer/tv/ProtectedTvPlayerApplication$ProtectedTvPlayerApplication$m;
.super Ljava/lang/Object;


# static fields
.field public static final fIxoi:Ljava/lang/String;

.field public static t:Ljava/lang/String;


# instance fields
.field public final CHuH:Z

.field public final JIIBd:Z

.field public final eE:Ljava/lang/String;

.field public final fa:Z

.field public final hokH:Z

.field public final jJw:Ljava/lang/String;

.field public final jgBmI:I

.field public final jwcl:Z

.field public final kH:Z

.field public final uGn:Z

.field public final uehBx:Z

.field public final ul:Z

.field public final vE:I


# direct methods
.method static constructor <clinit>()V
    .locals 9

    invoke-static {}, Ljava/util/UUID;->randomUUID()Ljava/util/UUID;

    move-result-object v0

    invoke-virtual {v0}, Ljava/util/UUID;->getMostSignificantBits()J

    move-result-wide v1

    invoke-virtual {v0}, Ljava/util/UUID;->getLeastSignificantBits()J

    move-result-wide v3

    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v5

    const/16 v0, 0x10

    shl-long/2addr v5, v0

    const-wide/32 v7, 0xffff

    and-long/2addr v1, v7

    or-long/2addr v1, v5

    new-instance v0, Ljava/util/UUID;

    invoke-direct {v0, v1, v2, v3, v4}, Ljava/util/UUID;-><init>(JJ)V

    invoke-virtual {v0}, Ljava/util/UUID;->toString()Ljava/lang/String;

    move-result-object v0

    sput-object v0, Lar/tvplayer/tv/ProtectedTvPlayerApplication$ProtectedTvPlayerApplication$m;->fIxoi:Ljava/lang/String;

    return-void
.end method

.method private constructor <init>(Ljava/lang/String;Ljava/lang/String;I)V
    .locals 1

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    sget-object p1, Lar/tvplayer/tv/ProtectedTvPlayerApplication$ProtectedTvPlayerApplication$m;->fIxoi:Ljava/lang/String;

    iput-object p1, p0, Lar/tvplayer/tv/ProtectedTvPlayerApplication$ProtectedTvPlayerApplication$m;->eE:Ljava/lang/String;

    iput-object p2, p0, Lar/tvplayer/tv/ProtectedTvPlayerApplication$ProtectedTvPlayerApplication$m;->jJw:Ljava/lang/String;

    iput p3, p0, Lar/tvplayer/tv/ProtectedTvPlayerApplication$ProtectedTvPlayerApplication$m;->vE:I

    and-int/lit8 p1, p3, 0x1

    int-to-byte p1, p1

    iput-boolean p1, p0, Lar/tvplayer/tv/ProtectedTvPlayerApplication$ProtectedTvPlayerApplication$m;->jwcl:Z

    ushr-int/lit8 p1, p3, 0x1

    and-int/lit8 p1, p1, 0x1

    int-to-byte p1, p1

    iput-boolean p1, p0, Lar/tvplayer/tv/ProtectedTvPlayerApplication$ProtectedTvPlayerApplication$m;->ul:Z

    ushr-int/lit8 p1, p3, 0x2

    and-int/lit8 p1, p1, 0x1

    int-to-byte p1, p1

    iput-boolean p1, p0, Lar/tvplayer/tv/ProtectedTvPlayerApplication$ProtectedTvPlayerApplication$m;->uGn:Z

    ushr-int/lit8 p1, p3, 0x3

    and-int/lit8 p1, p1, 0x1

    int-to-byte p1, p1

    iput-boolean p1, p0, Lar/tvplayer/tv/ProtectedTvPlayerApplication$ProtectedTvPlayerApplication$m;->uehBx:Z

    ushr-int/lit8 p1, p3, 0x4

    and-int/lit8 p1, p1, 0x1

    int-to-byte p1, p1

    iput-boolean p1, p0, Lar/tvplayer/tv/ProtectedTvPlayerApplication$ProtectedTvPlayerApplication$m;->CHuH:Z

    ushr-int/lit8 p1, p3, 0x5

    and-int/lit8 p1, p1, 0x1

    int-to-byte p1, p1

    iput-boolean p1, p0, Lar/tvplayer/tv/ProtectedTvPlayerApplication$ProtectedTvPlayerApplication$m;->kH:Z

    ushr-int/lit8 p1, p3, 0x6

    and-int/lit8 p1, p1, 0x1

    int-to-byte p1, p1

    iput-boolean p1, p0, Lar/tvplayer/tv/ProtectedTvPlayerApplication$ProtectedTvPlayerApplication$m;->fa:Z

    ushr-int/lit8 p1, p3, 0x7

    and-int/lit8 p1, p1, 0x1

    int-to-byte p1, p1

    iput-boolean p1, p0, Lar/tvplayer/tv/ProtectedTvPlayerApplication$ProtectedTvPlayerApplication$m;->JIIBd:Z

    ushr-int/lit8 p1, p3, 0x8

    and-int/lit8 p1, p1, 0x1

    int-to-byte p1, p1

    iput-boolean p1, p0, Lar/tvplayer/tv/ProtectedTvPlayerApplication$ProtectedTvPlayerApplication$m;->hokH:Z

    const/4 p1, 0x2

    iput p1, p0, Lar/tvplayer/tv/ProtectedTvPlayerApplication$ProtectedTvPlayerApplication$m;->jgBmI:I

    return-void
.end method

.method public static Hz(Ljava/lang/String;)V
    .locals 0

    return-void
.end method

.method public static native elAsxmc()Lar/tvplayer/tv/ProtectedTvPlayerApplication$ProtectedTvPlayerApplication$m;
.end method


# virtual methods
.method public native bfbnwthtCp(Ljava/lang/String;)Ljava/lang/String;
.end method
