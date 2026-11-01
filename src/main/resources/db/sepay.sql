SET XACT_ABORT ON;
BEGIN TRY
BEGIN TRANSACTION;
DECLARE @lock int;
EXEC @lock=sys.sp_getapplock @Resource=N'qldapm:sepay-schema',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=15000;
IF @lock<0 THROW 51000,'Cannot initialize SePay schema.',1;
IF OBJECT_ID(N'dbo.SePayAttempt') IS NULL
BEGIN
CREATE TABLE dbo.SePayAttempt (
 Id int IDENTITY(1,1) PRIMARY KEY CHECK(Id BETWEEN 1 AND 99999999),
 Reference AS ('QLD'+RIGHT('00000000'+CONVERT(varchar(8),Id),8)) PERSISTED UNIQUE,
 InvoiceId int NOT NULL REFERENCES dbo.HoaDon(MaHoaDon),
 Amount decimal(18,2) NOT NULL CHECK(Amount>0),
 Snapshot varchar(64) NOT NULL,
 Bank varchar(40) NOT NULL,
 AccountNumber varchar(40) NOT NULL,
 TestMode bit NOT NULL,
 Status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK(Status IN ('PENDING','PAID','CANCELLED','EXPIRED','REVIEW')),
 CreatedAt datetime2 NOT NULL DEFAULT SYSDATETIME(),
 ExpiresAt datetime2 NOT NULL,
 Note nvarchar(500),
 SettledEventId bigint
);
CREATE INDEX ix_sepay_invoice ON dbo.SePayAttempt(InvoiceId,Id DESC);
END;
IF OBJECT_ID(N'dbo.SePayEvent') IS NULL
BEGIN
CREATE TABLE dbo.SePayEvent (
 Id bigint PRIMARY KEY,
 Reference varchar(40),
 Bank varchar(40) NOT NULL,
 AccountNumber varchar(40) NOT NULL,
 Amount decimal(18,2) NOT NULL,
 TransferType varchar(10) NOT NULL,
 TransactionAt datetime2 NOT NULL,
 ReceivedAt datetime2 NOT NULL DEFAULT SYSDATETIME(),
 Status varchar(20) NOT NULL DEFAULT 'RECEIVED',
 Note nvarchar(500)
);
CREATE INDEX ix_sepay_event_reference ON dbo.SePayEvent(Reference);
END;
COMMIT;
END TRY
BEGIN CATCH
IF @@TRANCOUNT>0 ROLLBACK;
THROW;
END CATCH;
